package com.hardinfo.huaqin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ShellUtil {

    /**
     * <p>
     * Description:执行shell命令
     * <p>
     *
     * @param command shell
     * @return 返回命令输出结果
     * @throws IOException
     * @date:2014-3-14
     */
    public static String execCommand(String command) throws IOException {
        // shell与高级语言的调用
        Runtime runtime = Runtime.getRuntime();
        Process proc = runtime.exec(command);
        //执行时启动了一个子进程,因没有父进程的控制台而看不到输出，所以用输出流来得到shell执行后的结果
        InputStream inputStream = proc.getInputStream();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

        String line = "";
        StringBuilder result = new StringBuilder(line);
        while ((line = bufferedReader.readLine()) != null) {
            //shenlong add \n for zql1520 hardinfo
            result.append(line + "\n");
        }

        // 使用exec执行不会等执行成功以后才返回,它会立即返回;所以在某些情况下是很要命的(比如复制文件的时候)
        // 使用wairFor()可以等待命令执行完成以后才返回
        try {
            if (proc.waitFor() != 0) {
                System.err.println("exit value = " + proc.exitValue());
            }
        } catch (InterruptedException e) {
            System.err.println(e);
        }
        return result.toString();
    }
}
