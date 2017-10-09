package com.nervepoint.googletranslate;

import java.io.File;

public class Translatable {

	private File base;
	private File file;

	public Translatable(File file) {
		this(null, file);
	}

	public Translatable(File base, File file) {
		super();
		if(base != null && !file.getAbsolutePath().startsWith(base.getAbsolutePath())) {
			throw new IllegalArgumentException(file + " must be a decendent of " + base);
		}
		this.base = base;
		this.file = file;
	}

	public File getBase() {
		return base;
	}

	public File getFile() {
		return file;
	}

	public String getRelativePath() {
		return base == null ? file.getName() : file.getAbsolutePath().substring(base.getAbsolutePath().length());
	}

}
