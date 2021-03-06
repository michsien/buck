/**
 * Autogenerated by Thrift Compiler (0.9.3)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.facebook.buck.distributed.thrift;


import java.util.Map;
import java.util.HashMap;
import org.apache.thrift.TEnum;

public enum RuleKeyStatus implements org.apache.thrift.TEnum {
  UNKNOWN(0),
  NEVER_STORED(1),
  STORED_WITHIN_SLA(2),
  OUTSIDE_SLA(3);

  private final int value;

  private RuleKeyStatus(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  public static RuleKeyStatus findByValue(int value) { 
    switch (value) {
      case 0:
        return UNKNOWN;
      case 1:
        return NEVER_STORED;
      case 2:
        return STORED_WITHIN_SLA;
      case 3:
        return OUTSIDE_SLA;
      default:
        return null;
    }
  }
}
