/**
	 * Copyright (c) 2010-2019 Contributors to the openHAB project
	 *
	 * See the NOTICE file(s) distributed with this work for additional
	 * information.
	 *
	 * This program and the accompanying materials are made available under the
	 * terms of the Eclipse Public License 2.0 which is available at
	 * http://www.eclipse.org/legal/epl-2.0
	 *
	 * SPDX-License-Identifier: EPL-2.0
	 */

package org.openhab.binding.ipcamera.internal;

import static org.openhab.binding.ipcamera.IpCameraBindingConstants.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.ipcamera.handler.IpCameraHandler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

public class InstarHandler extends ChannelDuplexHandler {
    IpCameraHandler ipCameraHandler;
    private String requestUrl = "Empty";

    public InstarHandler(ThingHandler thingHandler) {
        ipCameraHandler = (IpCameraHandler) thingHandler;
    }

    public void setURL(String url) {
        requestUrl = url;
    }

    // This handles the incoming http replies back from the camera.
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = null;
        String value1 = null;

        try {
            content = msg.toString();
            if (content.isEmpty()) {
                return;
            }
            ipCameraHandler.logger.trace("HTTP Result back from camera is \t:{}:", content);

            switch (requestUrl) {
                case "/param.cgi?cmd=getinfrared":
                    if (content.contains("var infraredstat=\"auto")) {
                        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.valueOf("ON"));
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.valueOf("OFF"));
                    }
                    break;
                case "/param.cgi?cmd=getoverlayattr&-region=1":// Text Overlays
                    if (content.contains("var show_1=\"0\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_TEXT_OVERLAY, StringType.valueOf(""));
                    } else {
                        value1 = ipCameraHandler.searchString(content, "var name_1=\"");
                        if (value1 != null) {
                            ipCameraHandler.setChannelState(CHANNEL_TEXT_OVERLAY, StringType.valueOf(value1));
                        }
                    }
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getmdattr":// Motion Alarm
                    // Motion Alarm
                    if (content.contains("var m1_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.valueOf("ON"));
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.valueOf("OFF"));
                    }
                    // Reset the Alarm, need to find better place to put this.
                    ipCameraHandler.setChannelState(CHANNEL_MOTION_ALARM, OnOffType.valueOf("OFF"));
                    ipCameraHandler.firstMotionAlarm = false;
                    ipCameraHandler.motionAlarmUpdateSnapshot = false;
                    break;
                case "/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr":// Audio Alarm
                    if (content.contains("var aa_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.valueOf("ON"));
                        value1 = ipCameraHandler.searchString(content, "var aa_value=\"");
                        if (!value1.isEmpty()) {
                            ipCameraHandler.logger.debug("Threshold is changing to {}", value1);
                            ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, PercentType.valueOf(value1));
                        }
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.valueOf("OFF"));
                    }
                    // Reset the Alarm, need to find better place to put this.
                    ipCameraHandler.setChannelState(CHANNEL_AUDIO_ALARM, OnOffType.valueOf("OFF"));
                    ipCameraHandler.firstAudioAlarm = false;
                    ipCameraHandler.audioAlarmUpdateSnapshot = false;
                    break;
                case "param.cgi?cmd=getpirattr":// PIR Alarm
                    if (content.contains("var pir_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_PIR_ALARM, OnOffType.valueOf("ON"));
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_PIR_ALARM, OnOffType.valueOf("OFF"));
                    }
                    // Reset the Alarm, need to find better place to put this.
                    ipCameraHandler.setChannelState(CHANNEL_PIR_ALARM, OnOffType.valueOf("OFF"));
                    ipCameraHandler.firstMotionAlarm = false;
                    ipCameraHandler.motionAlarmUpdateSnapshot = false;
                    break;
                case "/param.cgi?cmd=getioattr":// External Alarm Input
                    if (content.contains("var io_enable=\"1\"")) {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.valueOf("ON"));
                    } else {
                        ipCameraHandler.setChannelState(CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT, OnOffType.valueOf("OFF"));
                    }
                    break;
            }
        } finally {
            ReferenceCountUtil.release(msg);
            content = value1 = null;
        }
    }

    public String encodeSpecialChars(String text) {
        String Processed = null;
        try {
            Processed = URLEncoder.encode(text, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {

        }
        return Processed;
    }

    // This handles the commands that come from the Openhab event bus.
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command.toString() == "REFRESH") {
            switch (channelUID.getId()) {
                case CHANNEL_MOTION_ALARM:
                    if (ipCameraHandler.serverPort > 0) {
                        ipCameraHandler.logger.info("Setting up the Alarm Server settings in the camera now");
                        ipCameraHandler.sendHttpGET(
                                "/param.cgi?cmd=setmdalarm&-aname=server2&-switch=on&cmd=setalarmserverattr&-as_index=3&-as_server="
                                        + ipCameraHandler.hostIp + "&-as_port=" + ipCameraHandler.serverPort
                                        + "&-as_path=/instar&-as_queryattr1=&-as_queryval1=&-as_queryattr2=&-as_queryval2=&-as_queryattr3=&-as_queryval3=&-as_activequery=1&-as_auth=0&-as_query1=0&-as_query2=0&-as_query3=0");
                        return;
                    }
            }
            return;
        } // end of "REFRESH"
        switch (channelUID.getId()) {
            case CHANNEL_THRESHOLD_AUDIO_ALARM:
                int value = Math.round(Float.valueOf(command.toString()));
                if (value == 0) {
                    ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
                } else {
                    ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
                    ipCameraHandler
                            .sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1&-aa_value="
                                    + command.toString());
                }
                return;
            case CHANNEL_ENABLE_AUDIO_ALARM:
                if ("ON".equals(command.toString())) {
                    ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=1");
                } else {
                    ipCameraHandler.sendHttpGET("/cgi-bin/hi3510/param.cgi?cmd=setaudioalarmattr&-aa_enable=0");
                }
                return;
            case CHANNEL_ENABLE_MOTION_ALARM:
                if ("ON".equals(command.toString())) {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=1&-name=1&cmd=setmdattr&-enable=1&-name=2&cmd=setmdattr&-enable=1&-name=3&cmd=setmdattr&-enable=1&-name=4");
                } else {
                    ipCameraHandler.sendHttpGET(
                            "/cgi-bin/hi3510/param.cgi?cmd=setmdattr&-enable=0&-name=1&cmd=setmdattr&-enable=0&-name=2&cmd=setmdattr&-enable=0&-name=3&cmd=setmdattr&-enable=0&-name=4");
                }
                return;
            case CHANNEL_TEXT_OVERLAY:
                String text = encodeSpecialChars(command.toString());
                if ("".contentEquals(text)) {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=0");
                } else {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setoverlayattr&-region=1&-show=1&-name=" + text);
                }
                return;
            case CHANNEL_AUTO_LED:
                if ("ON".equals(command.toString())) {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=auto");
                } else {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setinfrared&-infraredstat=close");
                }
                return;
            case CHANNEL_ENABLE_PIR_ALARM:
                if ("ON".equals(command.toString())) {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=1");
                } else {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setpirattr&-pir_enable=0");
                }
                return;
            case CHANNEL_ENABLE_EXTERNAL_ALARM_INPUT:
                if ("ON".equals(command.toString())) {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=1");
                } else {
                    ipCameraHandler.sendHttpGET("/param.cgi?cmd=setioattr&-io_enable=0");
                }
                return;
        }
    }

    public void alarmTriggered(String alarm) {
        ipCameraHandler.logger.debug("Alarm has been triggered:{}", alarm);
        switch (alarm) {
            case "/instar?&active=1":
                break;
            case "/instar?&active=2":
                break;
            case "/instar?&active=3":
                break;
            case "/instar?&active=4":
                break;
            case "/instar?&active=5":// PIR
                ipCameraHandler.motionDetected(CHANNEL_PIR_ALARM);
                break;
            case "/instar?&active=6":// Audio Alarm
                ipCameraHandler.audioDetected();
                break;
            case "/instar?&active=7":// Motion Area 1
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=8":// Motion Area 2
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=9":// Motion Area 3
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
            case "/instar?&active=10":// Motion Area 4
                ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
                break;
        }
    }

    // If a camera does not need to poll a request as often as snapshots, it can be
    // added here. Binding steps through the list.
    public ArrayList<String> getLowPriorityRequests() {
        ArrayList<String> lowPriorityRequests = new ArrayList<String>(2);
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getaudioalarmattr");
        lowPriorityRequests.add("/cgi-bin/hi3510/param.cgi?cmd=getmdattr");
        lowPriorityRequests.add("/param.cgi?cmd=getinfrared");
        lowPriorityRequests.add("/param.cgi?cmd=getoverlayattr&-region=1");
        lowPriorityRequests.add("/param.cgi?cmd=getpirattr");
        lowPriorityRequests.add("/param.cgi?cmd=getioattr"); // ext alarm input on/off
        // lowPriorityRequests.add("/param.cgi?cmd=getserverinfo");
        return lowPriorityRequests;
    }
}
