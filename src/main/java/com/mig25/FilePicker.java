package com.mig25;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;

/** Opens the operating system's "open file" window on its own thread, then calls back on the game thread. */
public final class FilePicker {
	private FilePicker() {}

	public static void pick(String title, Consumer<Path> callback) {
		if (GraphicsEnvironment.isHeadless()) {
			ModelManager.status = "No file window available here. Copy files into config/mig25/models instead.";
			return;
		}
		Thread t = new Thread(() -> {
			try {
				FileDialog fd = new FileDialog((Frame) null, title, FileDialog.LOAD);
				fd.setVisible(true);
				String f = fd.getFile();
				String d = fd.getDirectory();
				fd.dispose();
				if (f != null && d != null) {
					Path p = Path.of(d, f);
					Minecraft.getInstance().execute(() -> callback.accept(p));
				}
			} catch (Throwable e) {
				ModelManager.status = "File window failed: " + e;
			}
		}, "mig25-file-picker");
		t.setDaemon(true);
		t.start();
	}
}
