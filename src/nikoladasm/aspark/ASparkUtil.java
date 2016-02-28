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
import java.util.Properties;
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
	
	private static String processRegexPath(String path, String asteriskReplacement, String slashAsteriskReplacement) {
		String pathToUse = sanitizePath(path);
		int length = pathToUse.length();
		StringBuilder sb = new StringBuilder();
		boolean startWithWildcard = false;
		for (int i = 0; i < length; i++) {
			char c = pathToUse.charAt(i);
			if (i == 0 && c == '*') {
				sb.append(asteriskReplacement);
				startWithWildcard = true;
				continue;
			}
			if (i == length-2 && c == '/' && pathToUse.charAt(i+1) == '*') {
				if (startWithWildcard)
					throw new IllegalArgumentException("Path can't contain first and last star wildcard");
				sb.append(slashAsteriskReplacement);
				break;
			}
			if (i == length-1 && c == '*') {
				if (startWithWildcard)
					throw new IllegalArgumentException("Path can't contain first and last star wildcard");
				sb.append(asteriskReplacement);
				continue;
			}
			if (REGEXP_METACHARS.contains(String.valueOf(c))) {
				sb.append('\\').append(c);
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}
	
	public static Pattern buildParameterizedPathPattern(String path, Map<String, Integer> parameterNamesMap, Boolean startWithWildcard) {
		String pathToUse = processRegexPath(path, "(.*)", "(?:/?|/(.+))");
		Matcher parameterMatcher = PATTERN.matcher(pathToUse);
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
	
	public static Pattern buildPathPattern(String path) {
		String pathToUse = processRegexPath(path, ".*", "(?:/?|/.+)");
		return Pattern.compile("^"+pathToUse+"$");
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
		String pathToUse = path.trim();
		if (pathToUse.isEmpty()) return pathToUse;
		String rpath = pathToUse.replace(WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);
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
		if (!path.endsWith(FOLDER_SEPARATOR) && result.equals(FOLDER_SEPARATOR))
			result = result.substring(0, result.length()-1);
		return result;
	}
	
	public static boolean isEqualHttpMethod(HttpMethod requestHttpMethod,
			HttpMethod routeHttpMethod) {
		if (requestHttpMethod.equals(HEAD) && routeHttpMethod.equals(GET))
			return true;
		return requestHttpMethod.equals(routeHttpMethod);
	}
	
	public static ParamsMap parseParams(Map<String, List<String>> params) {
		ParamsMap result = new ParamsMap();
		params.forEach((keys, values) -> {
			ParamsMap root = result;
			Matcher keyMatcher = QK_PATTERN.matcher(keys);
			while (keyMatcher.find()) {
				String key = keyMatcher.group(1);
				root = root.createIfAbsentAndGet(key);
			}
			root.values(values.toArray(new String[values.size()]));
		});
		return result;
	}
	
	public static ParamsMap parseUniqueParams(Map<String, String> params) {
		ParamsMap result = new ParamsMap();
		params.forEach((key, value) -> {
			result.createIfAbsentAndGet(key).value(value);
		});
		return result;
	}
	
	public static String sanitizePath(String path) {
		String pathToUse = collapsePath(path);
		if (pathToUse.isEmpty()) return pathToUse;
		if (pathToUse.endsWith("/")) pathToUse = pathToUse.substring(0, pathToUse.length()-1);
		return pathToUse;
	}
	
	public static String mimeType(String file, Properties mimeTypes) {
		int extIndex = file.lastIndexOf('.');
		extIndex = (extIndex < 0 ) ? 0 : extIndex;
		String ext = file.substring(extIndex);
		return mimeTypes.getProperty(ext, "application/octet-stream");
	}
}
