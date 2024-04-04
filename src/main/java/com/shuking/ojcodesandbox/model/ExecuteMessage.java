package com.shuking.ojcodesandbox.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecuteMessage {

    // 编译退出状态码l
    private Integer exitValue;
    // 编译输出信息
    private String message;
    // 编译错误信息
    private String errorMessage;
    // 程序执行耗时
    private Long time;
    //程序占用内存
    private Long memory;
}
