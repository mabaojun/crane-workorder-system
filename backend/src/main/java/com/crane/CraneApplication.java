package com.crane;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 吊装业务管理系统后端入口。
 * 分层单体：modules 按业务域分包（auth/iam/staff/demand/workorder/dispatch/attendance/exception/system 等）。
 */
@SpringBootApplication
@MapperScan("com.crane.modules.**.mapper")
public class CraneApplication {

    public static void main(String[] args) {
        SpringApplication.run(CraneApplication.class, args);
    }
}
