package com.mig25;

import java.util.List;

/** A model ready to render: baked groups, its part names (for rigging) and its longest dimension (model units). */
public final class LoadedModel {
	public final String name;
	public final boolean builtin;
	public final JetModel.Group[] groups;
	public final List<String> partNames;
	public final double longest;

	public LoadedModel(String name, boolean builtin, JetModel.Group[] groups, List<String> partNames, double longest) {
		this.name = name;
		this.builtin = builtin;
		this.groups = groups;
		this.partNames = partNames;
		this.longest = longest;
	}

	public static LoadedModel builtin() {
		return new LoadedModel(ModelManager.BUILTIN, true, JetModel.groups, JetModel.partNames, JetConfig.MODEL_LENGTH);
	}

	/** Name shown in menus and on the HUD (no extension, no path). */
	public String displayName() {
		return ModelManager.displayName(name);
	}
}
