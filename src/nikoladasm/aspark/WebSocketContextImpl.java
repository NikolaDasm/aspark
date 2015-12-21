/*
 *  ASpark
 *  Copyright (C) 2015  Nikolay Platov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nikoladasm.aspark;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WebSocketContextImpl implements WebSocketContext {
	
	private Channel channel;
	private final AtomicReference<ByteBuf> frameBuffer = new AtomicReference<>();
	private AtomicReference<StringBuilder> stringBuilder = new AtomicReference<>();
	private AtomicBoolean textFrameBegin = new AtomicBoolean();
	
	public WebSocketContextImpl(Channel channel) {
		this.channel = channel;
		frameBuffer.set(Unpooled.buffer());
		stringBuilder.set(new StringBuilder());
		textFrameBegin.set(true);
	}
	
	@Override
	public void send(String msg) {
		channel.write(new TextWebSocketFrame(msg));
	}
	
	@Override
	public void send(byte[] msg) {
		channel.write(new BinaryWebSocketFrame(Unpooled.copiedBuffer(msg)));
	}
	
	public ByteBuf frameBuffer() {
		return frameBuffer.get();
	}
	
	public void frameBuffer(ByteBuf frameBuffer) {
		this.frameBuffer.set(frameBuffer);
	}
	
	public StringBuilder stringBuilder() {
		return stringBuilder.get();
	}
	
	public void stringBuilder(StringBuilder stringBuilder) {
		this.stringBuilder.set(stringBuilder);
	}
	
	public boolean textFrameBegin() {
		return textFrameBegin.get();
	}
	
	public void textFrameBegin(boolean textFrameBegin) {
		this.textFrameBegin.set(textFrameBegin);
	}
}
