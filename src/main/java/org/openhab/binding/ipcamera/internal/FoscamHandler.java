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

import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_AUDIO_ALARM;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_AUTO_LED;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_ENABLE_AUDIO_ALARM;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_ENABLE_LED;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_ENABLE_MOTION_ALARM;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_MOTION_ALARM;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CHANNEL_THRESHOLD_AUDIO_ALARM;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CONFIG_AUDIO_URL_OVERRIDE;
import static org.openhab.binding.ipcamera.IpCameraBindingConstants.CONFIG_MOTION_URL_OVERRIDE;

import java.util.ArrayList;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.ipcamera.handler.IpCameraHandler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;

public class FoscamHandler extends ChannelDuplexHandler {
	IpCameraHandler ipCameraHandler;
	String username, password;

	public FoscamHandler(ThingHandler handler, String username, String password) {
		ipCameraHandler = (IpCameraHandler) handler;
		this.username = username;
		this.password = password;
	}

	// This handles the incoming http replies back from the camera.
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		String content = null;
		try {
			content = msg.toString();
			if (!content.isEmpty()) {
				ipCameraHandler.logger.trace("HTTP Result back from camera is \t:{}:", content);
			} else {
				return;
			}

			////////////// Motion Alarm //////////////
			if (content.contains("<motionDetectAlarm>")) {
				if (content.contains("<motionDetectAlarm>0</motionDetectAlarm>")) {
					ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.valueOf("OFF"));
				} else if (content.contains("<motionDetectAlarm>1</motionDetectAlarm>")) { // Enabled but no alarm
					ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.valueOf("ON"));
					ipCameraHandler.setChannelState(CHANNEL_MOTION_ALARM, OnOffType.valueOf("OFF"));
					ipCameraHandler.firstMotionAlarm = false;
					ipCameraHandler.motionAlarmUpdateSnapshot = false;
				} else if (content.contains("<motionDetectAlarm>2</motionDetectAlarm>")) {// Enabled, alarm on
					ipCameraHandler.setChannelState(CHANNEL_ENABLE_MOTION_ALARM, OnOffType.valueOf("ON"));
					ipCameraHandler.motionDetected(CHANNEL_MOTION_ALARM);
				}
			}

			////////////// Sound Alarm //////////////
			if (content.contains("<soundAlarm>0</soundAlarm>")) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.valueOf("OFF"));
				ipCameraHandler.setChannelState(CHANNEL_AUDIO_ALARM, OnOffType.valueOf("OFF"));
			}
			if (content.contains("<soundAlarm>1</soundAlarm>")) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.valueOf("ON"));
				ipCameraHandler.setChannelState(CHANNEL_AUDIO_ALARM, OnOffType.valueOf("OFF"));
				ipCameraHandler.firstAudioAlarm = false;
				ipCameraHandler.audioAlarmUpdateSnapshot = false;
			}
			if (content.contains("<soundAlarm>2</soundAlarm>")) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_AUDIO_ALARM, OnOffType.valueOf("ON"));
				ipCameraHandler.audioDetected();
			}

			////////////// Sound Threshold //////////////
			if (content.contains("<sensitivity>0</sensitivity>")) {
				ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, PercentType.valueOf("0"));
			}
			if (content.contains("<sensitivity>1</sensitivity>")) {
				ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, PercentType.valueOf("50"));
			}
			if (content.contains("<sensitivity>2</sensitivity>")) {
				ipCameraHandler.setChannelState(CHANNEL_THRESHOLD_AUDIO_ALARM, PercentType.valueOf("100"));
			}

			//////////////// Infrared LED /////////////////////
			if (content.contains("<infraLedState>0</infraLedState>")) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, OnOffType.valueOf("OFF"));
			}
			if (content.contains("<infraLedState>1</infraLedState>")) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, OnOffType.valueOf("ON"));
			}

			if (content.contains("</CGI_Result>")) {
				ctx.close();
				ipCameraHandler.logger.debug("End of FOSCAM handler reached, so closing the channel to the camera now");
			}

		} finally {
			ReferenceCountUtil.release(msg);
			content = null;
		}
	}

	// This handles the commands that come from the Openhab event bus.
	public void handleCommand(ChannelUID channelUID, Command command) {
		if (command.toString() == "REFRESH") {
			switch (channelUID.getId()) {
			case CHANNEL_THRESHOLD_AUDIO_ALARM:
				ipCameraHandler.sendHttpGET(
						"/cgi-bin/CGIProxy.fcgi?cmd=getAudioAlarmConfig&usr=" + username + "&pwd=" + password);
				return;
			case CHANNEL_ENABLE_AUDIO_ALARM:
				ipCameraHandler.sendHttpGET(
						"/cgi-bin/CGIProxy.fcgi?cmd=getAudioAlarmConfig&usr=" + username + "&pwd=" + password);
				return;
			case CHANNEL_ENABLE_MOTION_ALARM:
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=getDevState&usr=" + username + "&pwd=" + password);
				return;
			}
			return; // Return as we have handled the refresh command above and don't need to
					// continue further.
		} // end of "REFRESH"
		switch (channelUID.getId()) {
		case CHANNEL_ENABLE_LED:
			// Disable the auto mode first
			ipCameraHandler.sendHttpGET(
					"/cgi-bin/CGIProxy.fcgi?cmd=setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
			ipCameraHandler.setChannelState(CHANNEL_AUTO_LED, OnOffType.valueOf("OFF"));
			if ("0".equals(command.toString()) || "OFF".equals(command.toString())) {
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=closeInfraLed&usr=" + username + "&pwd=" + password);
			} else {
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=openInfraLed&usr=" + username + "&pwd=" + password);
			}
			return;
		case CHANNEL_AUTO_LED:
			if ("ON".equals(command.toString())) {
				ipCameraHandler.setChannelState(CHANNEL_ENABLE_LED, UnDefType.valueOf("UNDEF"));
				ipCameraHandler.sendHttpGET(
						"/cgi-bin/CGIProxy.fcgi?cmd=setInfraLedConfig&mode=0&usr=" + username + "&pwd=" + password);
			} else {
				ipCameraHandler.sendHttpGET(
						"/cgi-bin/CGIProxy.fcgi?cmd=setInfraLedConfig&mode=1&usr=" + username + "&pwd=" + password);
			}
			return;
		case CHANNEL_THRESHOLD_AUDIO_ALARM:
			int value = Math.round(Float.valueOf(command.toString()));
			if (value == 0) {
				ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=0&usr=" + username
						+ "&pwd=" + password);
			} else if (value <= 33) {
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=1&sensitivity=0&usr="
								+ username + "&pwd=" + password);
			} else if (value <= 66) {
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=1&sensitivity=1&usr="
								+ username + "&pwd=" + password);
			} else {
				ipCameraHandler
						.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=1&sensitivity=2&usr="
								+ username + "&pwd=" + password);
			}
			return;
		case CHANNEL_ENABLE_AUDIO_ALARM:
			if ("ON".equals(command.toString())) {
				if (ipCameraHandler.config.get(CONFIG_AUDIO_URL_OVERRIDE) == null) {
					ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=1&usr="
							+ username + "&pwd=" + password);
				} else {
					ipCameraHandler.sendHttpGET(ipCameraHandler.config.get(CONFIG_AUDIO_URL_OVERRIDE).toString());
				}
			} else {
				ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setAudioAlarmConfig&isEnable=0&usr=" + username
						+ "&pwd=" + password);
			}
			return;
		case CHANNEL_ENABLE_MOTION_ALARM:
			if ("ON".equals(command.toString())) {
				if (ipCameraHandler.config.get(CONFIG_MOTION_URL_OVERRIDE) == null) {
					ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setMotionDetectConfig&isEnable=1&usr="
							+ username + "&pwd=" + password);
					ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setMotionDetectConfig1&isEnable=1&usr="
							+ username + "&pwd=" + password);
				} else {
					ipCameraHandler.sendHttpGET(ipCameraHandler.config.get(CONFIG_MOTION_URL_OVERRIDE).toString());
				}
			} else {
				ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setMotionDetectConfig&isEnable=0&usr="
						+ username + "&pwd=" + password);
				ipCameraHandler.sendHttpGET("/cgi-bin/CGIProxy.fcgi?cmd=setMotionDetectConfig1&isEnable=0&usr="
						+ username + "&pwd=" + password);
			}
			return;
		}
	}

	// If a camera does not need to poll a request as often as snapshots, it can be
	// added here. Binding steps through the list.
	public ArrayList<String> getLowPriorityRequests() {
		ArrayList<String> lowPriorityRequests = new ArrayList<String>(1);
		lowPriorityRequests.add("/cgi-bin/CGIProxy.fcgi?cmd=getDevState&usr=" + username + "&pwd=" + password);
		return lowPriorityRequests;
	}
}