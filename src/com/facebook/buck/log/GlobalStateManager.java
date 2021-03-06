/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.log;

import com.facebook.buck.model.BuildId;
import com.facebook.buck.util.Verbosity;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import javax.annotation.Nullable;

public class GlobalStateManager {
  private static final Logger LOG = Logger.get(GlobalStateManager.class);

  private static final GlobalStateManager SINGLETON = new GlobalStateManager();
  private static final String DEFAULT_LOG_FILE_WRITER_KEY = "DEFAULT";

  // Shared global state.
  private final ConcurrentMap<Long, String> threadIdToCommandId;

  // Global state required by the ConsoleHandler.
  private final ConcurrentMap<String, OutputStreamWriter> commandIdToConsoleHandlerWriter;
  private final ConcurrentMap<String, Level> commandIdToConsoleHandlerLevel;

  // Global state required by the LogFileHandler.
  private final ConcurrentMap<String, Writer> commandIdToLogFileHandlerWriter;

  public static GlobalStateManager singleton() {
    return SINGLETON;
  }

  public GlobalStateManager() {
    this.threadIdToCommandId = new ConcurrentHashMap<>();
    this.commandIdToConsoleHandlerWriter = new ConcurrentHashMap<>();
    this.commandIdToConsoleHandlerLevel = new ConcurrentHashMap<>();
    this.commandIdToLogFileHandlerWriter = new ConcurrentHashMap<>();

    rotateDefaultLogFileWriter(
        InvocationInfo.of(new BuildId(), "launch", LogConfigSetup.DEFAULT_SETUP.getLogDir())
            .getLogFilePath());
  }

  public Closeable setupLoggers(
      InvocationInfo info,
      OutputStream consoleHandlerStream,
      final OutputStream consoleHandlerOriginalStream,
      final Verbosity consoleHandlerVerbosity) {
    ReferenceCountedWriter writer = rotateDefaultLogFileWriter(info.getLogFilePath());

    final long threadId = Thread.currentThread().getId();
    final String commandId = info.getCommandId();

    // Setup the shared state.
    threadIdToCommandId.putIfAbsent(threadId, commandId);

    // Setup the ConsoleHandler state.
    commandIdToConsoleHandlerWriter.put(
        commandId,
        ConsoleHandler.utf8OutputStreamWriter(consoleHandlerStream));
    if (Verbosity.ALL.equals(consoleHandlerVerbosity)) {
      commandIdToConsoleHandlerLevel.put(commandId, Level.ALL);
    }

    // Setup the LogFileHandler state.
    Path logDirectory = info.getLogDirectoryPath();
    try {
      Files.createDirectories(logDirectory);
    } catch (IOException e) {
      LOG.error(
          e,
          "Failed to created 'per command log directory': [%s]",
          logDirectory.toAbsolutePath());
    }

    commandIdToLogFileHandlerWriter.put(
        commandId,
        writer.newReference());

    return new Closeable() {
      @Override
      public void close() throws IOException {

        // Tear down the LogFileHandler state.
        Writer writer = commandIdToLogFileHandlerWriter.remove(commandId);
        if (writer != null) {
          writer.flush();
          writer.close();
        }

        // Tear down the ConsoleHandler state.
        commandIdToConsoleHandlerWriter.put(
            commandId,
            ConsoleHandler.utf8OutputStreamWriter(consoleHandlerOriginalStream));
        commandIdToConsoleHandlerLevel.remove(commandId);

        // Tear down the shared state.
        // NOTE: Avoid iterator in case there's a concurrent change to this map.
        List<Long> allKeys = Lists.newArrayList(threadIdToCommandId.keySet());
        for (Long threadId : allKeys) {
          if (commandId.equals(threadIdToCommandId.get(threadId))) {
            threadIdToCommandId.remove(threadId);
          }
        }
      }
    };
  }

  private ReferenceCountedWriter newReferenceCounterWriter(String filePath)
      throws FileNotFoundException {
    return new ReferenceCountedWriter(
        ConsoleHandler.utf8OutputStreamWriter(
            new FileOutputStream(filePath)));
  }

  private ReferenceCountedWriter rotateDefaultLogFileWriter(Path logFilePath) {
    try {
      Files.createDirectories(logFilePath.getParent());
      ReferenceCountedWriter newWriter = newReferenceCounterWriter(logFilePath.toString());
      Writer oldWriter = commandIdToLogFileHandlerWriter.get(
          DEFAULT_LOG_FILE_WRITER_KEY);
      commandIdToLogFileHandlerWriter.put(DEFAULT_LOG_FILE_WRITER_KEY, newWriter);

      if (oldWriter != null) {
        oldWriter.flush();
        oldWriter.close();
      }
      return newWriter;

    } catch (FileNotFoundException e) {
      throw new RuntimeException(String.format("Could not create file [%s].", logFilePath), e);
    } catch (IOException e) {
      throw new RuntimeException(String.format("Exception wrapping file [%s].", logFilePath), e);
    }
  }

  public CommonThreadFactoryState getThreadToCommandRegister() {
    return new CommonThreadFactoryState() {
      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }

      @Override
      public void register(long threadId, String commandId) {
        threadIdToCommandId.put(threadId, commandId);
      }
    };
  }

  public ConsoleHandlerState getConsoleHandlerState() {
    return new ConsoleHandlerState() {
      @Override
      public OutputStreamWriter getWriter(String commandId) {
        return commandIdToConsoleHandlerWriter.get(commandId);
      }

      @Override
      public Iterable<OutputStreamWriter> getAllAvailableWriters() {
        return commandIdToConsoleHandlerWriter.values();
      }

      @Override
      public Level getLogLevel(String commandId) {
        return commandIdToConsoleHandlerLevel.get(commandId);
      }

      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }
    };
  }

  public ThreadIdToCommandIdMapper getThreadIdToCommandIdMapper() {
    return new ThreadIdToCommandIdMapper() {
      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }
    };
  }

  public LogFileHandlerState getLogFileHandlerState() {
    return new LogFileHandlerState() {
      @Override
      public Iterable<Writer> getWriters(@Nullable String commandId) {
        if (commandId == null) {
          return commandIdToLogFileHandlerWriter.values();
        }

        Writer writer = commandIdToLogFileHandlerWriter.get(commandId);
        if (writer != null) {
          return Collections.singleton(writer);
        } else {
          return commandIdToLogFileHandlerWriter.values();
        }
      }

      @Override
      public String threadIdToCommandId(long threadId) {
        return threadIdToCommandId.get(threadId);
      }
    };
  }

  /**
   * Since this is a Singleton class, make sure it cleans after itself once it's GC'ed.
   *
   * @exception  IOException  if an I/O error occurs.
   */
  @Override
  protected void finalize() throws IOException {
    // Close off any writers that may still be hanging about.
    for (Writer writer : Iterables.concat(
        commandIdToConsoleHandlerWriter.values(),
        commandIdToLogFileHandlerWriter.values())) {
      try {
        writer.close();
      } catch (IOException e) {
        // Keep going through all the writers even if one fails.
        LOG.error(e, "Failed to cleanly close() and OutputStreamWriter.");
      }
    }
  }
}
