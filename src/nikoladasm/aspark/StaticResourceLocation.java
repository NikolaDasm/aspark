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

import static nikoladasm.aspark.ASparkUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StaticResourceLocation {

	public static class StaticResource {
		private InputStream stream;
		private String fullPath;
		
		public StaticResource(InputStream stream, String fullPath) {
			this.stream = stream;
			this.fullPath = fullPath;
		}
		
		public InputStream stream() {
			return stream;
		}
		
		public String fullPath() {
			return fullPath;
		}
	}
	
	private ConcurrentLinkedQueue<ACLEntry> acl;

	private String folder;
	private String[] indexFiles;
	
	public StaticResourceLocation(String folder, String[] indexFiles) {
		String pathToUse = sanitizePath(folder);
		this.folder = pathToUse;
		this.indexFiles = indexFiles;
		acl = new ConcurrentLinkedQueue<>();
	}
	
	public String folder() {
		return folder;
	}
	
	public String[] indexFiles() {
		return indexFiles;
	}

	public void aclEntry(ACLEntry aclEntry) {
		acl.add(aclEntry);
	}
	
	private boolean isAllowedExtension(String path) {
		if (acl.size() == 0) return true;
		for (ACLEntry entry : acl) {
			Boolean allowed = entry.isAllowed(path);
			if (allowed != null) return allowed;
		}
		return false;
	}
	
	public StaticResource getClassResource(String path) {
		String pathToUse = sanitizePath(path);
		String fullPath = folder+pathToUse;
		if (!isAllowedExtension(fullPath))
			new StaticResource(null, "");
		InputStream input;
		input = this.getClass().getResourceAsStream(fullPath);
		if (input != null) return new StaticResource(input, fullPath);
		input = this.getClass().getClassLoader().getResourceAsStream(fullPath);
		if (input != null) return new StaticResource(input, fullPath);
		for (int i=0; i<indexFiles.length; i++) {
			String fName = fullPath+"/"+indexFiles[i];
			input = this.getClass().getResourceAsStream(fName);
			if (input != null) return new StaticResource(input, fName);
			input = this.getClass().getClassLoader().getResourceAsStream(fName);
			if (input != null) return new StaticResource(input, fName);
		}
		return new StaticResource(null, "");
	}
	
	public StaticResource getFileResource(String path) {
		String pathToUse = sanitizePath(path);
		String fullPath = folder+pathToUse;
		if (!isAllowedExtension(fullPath))
			new StaticResource(null, "");
		File file;
		file = new File(fullPath);
		if (file.exists() && !file.isDirectory()) {
			try {
				return new StaticResource(new FileInputStream(file), fullPath);
			} catch (FileNotFoundException e) {}
		}
		for (int i=0; i<indexFiles.length; i++) {
			String fName = fullPath+"/"+indexFiles[i];
			file = new File(fName);
			if (file.exists() && !file.isDirectory()) {
				try {
					return new StaticResource(new FileInputStream(file), fName);
				} catch (FileNotFoundException e) {}
			}
		}
		return new StaticResource(null, "");	
	}
}
