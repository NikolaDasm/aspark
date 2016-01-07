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

package nikoladasm.common;

import java.util.Iterator;
import java.util.function.Function;

public class FilterableWrapper<T> implements Iterable<T> {

	private Iterable<T> irerable;
	private Function<T,Boolean> filter;
	
	public FilterableWrapper(Iterable<T> irerable, Function<T,Boolean> filter) {
		this.irerable = irerable;
		this.filter = filter;
	}

	@Override
	public Iterator<T> iterator() {
		return new FilteredIterator<T>(irerable.iterator(), filter);
	}
}
