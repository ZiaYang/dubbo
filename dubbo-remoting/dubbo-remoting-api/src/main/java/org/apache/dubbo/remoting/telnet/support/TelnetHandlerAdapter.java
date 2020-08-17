/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.telnet.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.telnet.TelnetHandler;
import org.apache.dubbo.remoting.transport.ChannelHandlerAdapter;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.remoting.Constants.TELNET;

/**
 *
 * 完成Telnet指令转发的核心实现类是TelnetHandlerAdapter。
 * 它的实现非常简单，首先将用户输入的指令识别成commandC比如invoke>Is和status),然后将剩余的内容解析成message。
 * message会交给命令实现者去处理。实现代码类在TelnetHandlerAdapter#telnet中。
 */
public class TelnetHandlerAdapter extends ChannelHandlerAdapter implements TelnetHandler {

    private final ExtensionLoader<TelnetHandler> extensionLoader = ExtensionLoader.getExtensionLoader(TelnetHandler.class);

    @Override
    public String telnet(Channel channel, String message) throws RemotingException {
        String prompt = channel.getUrl().getParameterAndDecoded(Constants.PROMPT_KEY, Constants.DEFAULT_PROMPT);
        boolean noprompt = message.contains("--no-prompt");
        message = message.replace("--no-prompt", "");
        StringBuilder buf = new StringBuilder();
        message = message.trim();
        String command;
        if (message.length() > 0) {
            //找到消息字符串中的第一个空格，准备将命令和命令参数分开提取
            int i = message.indexOf(' ');
            if (i > 0) {
                command = message.substring(0, i).trim();//提取执行命令
                message = message.substring(i + 1).trim();//提取命令后的所有字符串
            } else {
                command = message;
                message = "";
            }
        } else {
            command = "";
        }

        if (command.length() > 0) {
            //检查系统是否有命令对应的扩展点
            if (extensionLoader.hasExtension(command)) {
                if (commandEnabled(channel.getUrl(), command)) {
                    try {
                        //交给具体扩展点执行
                        String result = extensionLoader.getExtension(command).telnet(channel, message);
                        if (result == null) {
                            return null;
                        }
                        buf.append(result);
                    } catch (Throwable t) {
                        buf.append(t.getMessage());
                    }
                } else {
                    buf.append("Command: ");
                    buf.append(command);
                    buf.append(" disabled");
                }
            } else {
                buf.append("Unsupported command: ");
                buf.append(command);
            }
        }

        if (buf.length() > 0) {
            buf.append("\r\n");//在telnet消息结尾追加回车和换行符
        }
        if (StringUtils.isNotEmpty(prompt) && !noprompt) {
            buf.append(prompt);
        }
        return buf.toString();
    }

    private boolean commandEnabled(URL url, String command) {
        String supportCommands = url.getParameter(TELNET);
        if (StringUtils.isEmpty(supportCommands)) {
            return true;
        }
        String[] commands = COMMA_SPLIT_PATTERN.split(supportCommands);
        for (String c : commands) {
            if (command.equals(c)) {
                return true;
            }
        }
        return false;
    }

}
