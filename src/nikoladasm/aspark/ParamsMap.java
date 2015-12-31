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

import java.util.HashMap;
import java.util.Map;

public class ParamsMap {
	
	private final Map<String, ParamsMap> queryMap;
	private String[] values;
	
	public ParamsMap() {
		queryMap = new HashMap<>();
	}

	public Map<String, ParamsMap> getQueryMap() {
		return queryMap;
	}
	
	public ParamsMap get(String key) {
		return queryMap.getOrDefault(key, new ParamsMap());
	}
	
	public ParamsMap createIfAbsentAndGet(String key) {
		queryMap.putIfAbsent(key, new ParamsMap());
		return queryMap.get(key);
	}
	
	public ParamsMap get(String... keys) {
		ParamsMap root = this;
		for (String key : keys) {
			root = get(key);
		}
		return root;
	}
	
	public String value(String... keys) {
		return get(keys).value();
	}
	
	public String[] values() {
		return values;
	}
	
	public void values(String[] values) {
		this.values = values;
	}
	
	public String value() {
		return hasValue() ? values[0] : null;
	}
	
	public Boolean booleanValue() {
		return hasValue() ? Boolean.valueOf(value()) : null;
	}
	
	public Integer integerValue() {
		return hasValue() ? Integer.valueOf(value()) : null;
	}
	
	public Long longValue() {
		return hasValue() ? Long.valueOf(value()) : null;
	}
	
	public Float floatValue() {
		return hasValue() ? Float.valueOf(value()) : null;
	}
	
	public Double doubleValue() {
		return hasValue() ? Double.valueOf(value()) : null;
	}
	
	public boolean isEmpty() {
		return queryMap.isEmpty();
	}

	public boolean hasValue() {
		return (values == null) ? false : values.length > 0;
	}
}
