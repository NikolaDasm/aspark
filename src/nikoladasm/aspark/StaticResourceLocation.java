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

import static nikoladasm.aspark.ASparkUtil.collapsePath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class StaticResourceLocation {
	private String folder;
	private String[][] configuration;
	
	public StaticResourceLocation(String folder, String[][] configuration) {
		String pathToUse = collapsePath(folder);
		if (pathToUse.endsWith("/")) pathToUse = pathToUse.substring(0, pathToUse.length()-1);
		this.folder = pathToUse;
		this.configuration = configuration;
	}
	
	public String folder() {
		return folder;
	}
	
	public String[][] configuration() {
		return configuration;
	}
	
	public InputStream getClassInputStream(String path) {
		String pathToUse = collapsePath(path);
		if (pathToUse.endsWith("/")) pathToUse = pathToUse.substring(0, pathToUse.length()-1);
		String fullPath = folder+pathToUse;
		InputStream input;
		for (int i=0; i<configuration[0].length; i++) {
			if (configuration[0][i].equals("*") || pathToUse.endsWith("."+configuration[0][i])) {
				input = this.getClass().getResourceAsStream(fullPath);
				if (input != null) return input;
				input = this.getClass().getClassLoader().getResourceAsStream(fullPath);
				if (input != null) return input;
			}
		}
		for (int i=0; i<configuration[1].length; i++) {
			input = this.getClass().getResourceAsStream(fullPath+"/"+configuration[1][i]);
			if (input != null) return input;
			input = this.getClass().getClassLoader().getResourceAsStream(fullPath+"/"+configuration[1][i]);
			if (input != null) return input;
		}
		return null;	
	}
	
	public InputStream getFileInputStream(String path) {
		String pathToUse = collapsePath(path);
		if (pathToUse.endsWith("/")) pathToUse = pathToUse.substring(0, pathToUse.length()-1);
		String fullPath = folder+pathToUse;
		File file;
		for (int i=0; i<configuration[0].length; i++) {
			if (configuration[0][i].equals("*") || pathToUse.endsWith("."+configuration[0][i])) {
				file = new File(fullPath);
				if (file.exists() && !file.isDirectory()) {
					try {
						return new FileInputStream(fullPath);
					} catch (FileNotFoundException e) {
						return null;
					}
				}
			}
		}
		for (int i=0; i<configuration[1].length; i++) {
			file = new File(fullPath+"/"+configuration[1][i]);
			if (file.exists() && !file.isDirectory()) {
				try {
					return new FileInputStream(fullPath);
				} catch (FileNotFoundException e) {
					return null;
				}
			}
		}
		return null;	
	}
}
