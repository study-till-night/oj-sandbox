package com.shuking.ojcodesandbox.controller;

import cn.hutool.core.io.resource.ResourceUtil;
import com.shuking.ojcodesandbox.model.ExecuteCodeRequest;
import com.shuking.ojcodesandbox.model.ExecuteCodeResponse;
import com.shuking.ojcodesandbox.service.JavaDockerCodeSandBox;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RestController
@RequestMapping("/sandbox")
public class MainController {

    @GetMapping("/health")
    public ExecuteCodeResponse healthCheck() {
        JavaDockerCodeSandBox javaBox = new JavaDockerCodeSandBox();

        ExecuteCodeRequest request = new ExecuteCodeRequest(Arrays.asList("1 2", "100 200"), "java",
                ResourceUtil.readStr("testCode/inter/Main.java", StandardCharsets.UTF_8));
        ExecuteCodeResponse executeCode = javaBox.executeCode(request);
        return executeCode;
    }
}
