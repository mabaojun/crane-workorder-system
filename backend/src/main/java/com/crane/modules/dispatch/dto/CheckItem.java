package com.crane.modules.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前置校验单项。code 取值：VEHICLE_IDLE、STAFF_ON_DUTY、STAFF_NO_ACTIVE_ORDER、
 * PENDING_DISPATCH_LIMIT、TIME_VALID。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckItem {

    /** 校验项编码 */
    private String code;

    /** 是否通过 */
    private boolean pass;

    /** 未通过时的提示信息 */
    private String message;

    public static CheckItem pass(String code) {
        return new CheckItem(code, true, null);
    }

    public static CheckItem fail(String code, String message) {
        return new CheckItem(code, false, message);
    }
}
