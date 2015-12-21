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

public class ASparkException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private String message;
	private Exception exception;
	
	public ASparkException(String message, Exception exception) {
		this.message = message;
		this.exception = exception;
	}
	
	public ASparkException(String message) {
		this(message, null);
	}

	@Override
	public String getMessage() {
		return toString();
	}
	
	@Override
	public String toString() {
		return (exception == null) ? message : message + " " + exception.getMessage();
	}
}
