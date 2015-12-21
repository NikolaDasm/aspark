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

import java.io.InputStream;
import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.UTF_8;

@FunctionalInterface
public interface ResponseTransformer {
	Object transform(Object body) throws Exception;
	
	default byte[] serialize (Object body) throws Exception {
		Object afterTransformation = transform(body);
		if (afterTransformation instanceof byte[])
			return (byte[]) afterTransformation;
		if (afterTransformation instanceof ByteBuffer)
			return ((ByteBuffer) afterTransformation).array();
		if (afterTransformation instanceof InputStream) {
			byte[] result = new byte[((InputStream) afterTransformation).available()];
			((InputStream) afterTransformation).read(result);
			return result;
		}
		if (afterTransformation instanceof String)
			return ((String) afterTransformation).getBytes(UTF_8);
		return afterTransformation.toString().getBytes(UTF_8);
	}
}
