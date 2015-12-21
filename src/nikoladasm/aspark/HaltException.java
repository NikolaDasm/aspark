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

public class HaltException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private static final int DEFAULT_STATUS_CODE = 500;
	
	private int statusCode;
	private String body;
	
	public HaltException() {
		this(DEFAULT_STATUS_CODE, null);
	}
	
	public HaltException(int statusCode) {
		this(statusCode, null);
	}
	
	public HaltException(String body) {
		this(DEFAULT_STATUS_CODE, body);
	}
	
	public HaltException(int statusCode, String body) {
		this.statusCode = statusCode;
		this.body = body;
	}
	
	public int status() {
		return statusCode;
	}
	
	public String body() {
		return body;
	}
	
	@Override
	public String toString() {
		return "Halt Exception. Code: "+statusCode;
	}
	
	@Override
	public String getMessage() {
		return toString();
	}
}