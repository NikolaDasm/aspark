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
import java.util.NoSuchElementException;
import java.util.function.Function;

public class FilteredIterator<T> implements Iterator<T> {

	private Function<T,Boolean> filter;
	private Iterator<T> iterator;
	private T filteredValue = null;
	
	public FilteredIterator(Iterator<T> iterator, Function<T,Boolean> filter) {
		this.iterator = iterator;
		this.filter = filter;
	}
	
	@Override
	public boolean hasNext() {
		if (filteredValue != null) return true;
		while(iterator.hasNext()) {
			T value = iterator.next();
			if (filter.apply(value)) {
				filteredValue = value;
				return true;
			}
		}
		return false;
	}

	private T returnValue() {
		T value = filteredValue;
		filteredValue = null;
		return value;
	}
	
	@Override
	public T next() {
		if (filteredValue != null || hasNext())
			return returnValue();
		else
			throw new NoSuchElementException("No such element");
	}

}
