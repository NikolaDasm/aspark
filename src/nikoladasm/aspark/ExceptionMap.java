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

import java.util.concurrent.ConcurrentHashMap;

public class ExceptionMap {

	private final ConcurrentHashMap<Class<? extends Exception>, ExceptionHandler> exceptionMap;
	
	public ExceptionMap() {
		exceptionMap = new ConcurrentHashMap<>();
	}
	
	public void put(Class<? extends Exception> exceptionClass, ExceptionHandler handler) {
		exceptionMap.put(exceptionClass, handler);
	}
	
	public ExceptionHandler get(Class<? extends Exception> exceptionClass) {
		if (!exceptionMap.containsKey(exceptionClass)) {
			Class<?> superclass = exceptionClass.getSuperclass();
			do {
				if (exceptionMap.containsKey(superclass)) {
					ExceptionHandler handler = exceptionMap.get(superclass);
					exceptionMap.put(exceptionClass, handler);
					return handler;
				}
			} while (superclass != null);
			exceptionMap.put(exceptionClass, null);
		}
		return exceptionMap.get(exceptionClass);
	}
}
