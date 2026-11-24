package com.mig25.loader;

/** Thrown with a human-readable explanation when a file cannot be imported. */
public class UnsupportedFormatException extends Exception {
	private static final long serialVersionUID = 1L;

	public UnsupportedFormatException(String message) {
		super(message);
	}
}
