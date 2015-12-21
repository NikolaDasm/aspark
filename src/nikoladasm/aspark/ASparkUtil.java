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

import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;

import static nikoladasm.aspark.HttpMethod.*;

public final class ASparkUtil {
	
	private static final String PARAMETERS_PATTERN = "(?i)(:[A-Z_][A-Z_0-9]*)";
	private static final Pattern PATTERN = Pattern.compile(PARAMETERS_PATTERN);
	private static final String DEFAULT_ACCEPT_TYPE = "*/*";
	private static final String REGEXP_METACHARS = "<([{\\^-=$!|]})?*+.>";
	private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
	private static final String FOLDER_SEPARATOR = "/";
	private static final String WINDOWS_FOLDER_SEPARATOR = "\\";
	private static final String TOP_PATH = "..";
	private static final String CURRENT_PATH = ".";
	private static final String QUERY_KEYS_PATTERN = "\\s*\\[?\\s*([^\\]\\[\\s]+)\\s*\\]?\\s*";
	private static final Pattern QK_PATTERN = Pattern.compile(QUERY_KEYS_PATTERN);
	
	private ASparkUtil() {}
	
	public static Pattern buildPathPattern(String path, Map<String, Integer> parameterNamesMap) {
		String pathAfterTest = new String(path);
		int length = pathAfterTest.length();
		for (int i = 0; i < length; i++) {
			char c = pathAfterTest.charAt(i);
			if (i == length-1 && c == '*') {
				pathAfterTest = path.replace("*", "(.*)");
				break;
			}
			if (REGEXP_METACHARS.contains(String.valueOf(c)))
				throw new IllegalArgumentException("Path can't contain regexp metachars");
		}
		Matcher parameterMatcher = PATTERN.matcher(pathAfterTest);
		int i = 1;
		while (parameterMatcher.find()) {
			String parameterName = parameterMatcher.group(1);
			if (parameterNamesMap.containsKey(parameterName))
				throw new ASparkException("Duplicate parameter name.");
			parameterNamesMap.put(parameterName, i);
			i++;
		}
		return Pattern.compile("^"+parameterMatcher.replaceAll("([^/]+)")+"$");
	}
	
	public static boolean isAcceptContentType(String requestAcceptTypes,
			String routeAcceptType) {
		if (requestAcceptTypes == null)
			return routeAcceptType.trim().equals(DEFAULT_ACCEPT_TYPE);
		String[] requestAcceptTypesArray = requestAcceptTypes.split(",");
		String[] rtat = routeAcceptType.trim().split("/");
		for (int i=0; i<requestAcceptTypesArray.length; i++) {
			String requestAcceptType = requestAcceptTypesArray[i].split(";")[0];
			String[] rqat = requestAcceptType.trim().split("/");
			if (((rtat[0].equals("*")) ? true : rqat[0].trim().equals(rtat[0])) &&
			((rtat[1].equals("*")) ? true : rqat[1].equals(rtat[1]))) return true;
		}
		return false;
	}
	
	public static long copyStreamToByteBuf(InputStream input, ByteBuf buf) throws IOException {
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		long count = 0;
		int n = 0;
		while ((n = input.read(buffer)) != -1) {
			buf.writeBytes(buffer, 0, n);
			count += n;
		}
		return count;
	}
	
	public static String collapsePath(String path) {
		String rpath = path.replace(WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);
		String[] directories = rpath.split(FOLDER_SEPARATOR);
		Deque<String> newDirectories = new LinkedList<>();
		for (int i=0; i<directories.length; i++) {
			String directory = directories[i].trim();
			if (directory.equals(TOP_PATH) && !newDirectories.isEmpty())
				newDirectories.removeLast();
			else if (!directory.equals(CURRENT_PATH) && !directory.isEmpty())
				newDirectories.addLast(directory);
		}
		String result = FOLDER_SEPARATOR;
		for (String directory : newDirectories)
			result += directory + FOLDER_SEPARATOR;
		if (!path.startsWith(FOLDER_SEPARATOR))
			result = result.substring(1);
		if (!path.endsWith(FOLDER_SEPARATOR) && !result.equals(FOLDER_SEPARATOR))
			result = result.substring(0, result.length()-1);
		return result;
	}
	
	public static boolean isEqualHttpMethod(HttpMethod requestHttpMethod,
			HttpMethod routeHttpMethod) {
		if (requestHttpMethod.equals(HEAD) && routeHttpMethod.equals(GET))
			return true;
		return requestHttpMethod.equals(routeHttpMethod);
	}
	
	public static QueryParamsMap parseQueryParams(Map<String, List<String>> params) {
		QueryParamsMap result = new QueryParamsMap();
		params.forEach((keys, values) -> {
			QueryParamsMap root = result;
			Matcher keyMatcher = QK_PATTERN.matcher(keys);
			while (keyMatcher.find()) {
				String key = keyMatcher.group(1);
				root = root.createIfAbsentAndGet(key);
			}
			root.values(values.toArray(new String[values.size()]));
		});
		return result;
	}
}
