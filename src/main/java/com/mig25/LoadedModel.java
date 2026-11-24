package com.mig25;

/** A model ready to render: baked groups plus its longest dimension (in model units). */
public final class LoadedModel {
	public final String name;
	public final boolean builtin;
	public final JetModel.Group[] groups;
	public final double longest;

	public LoadedModel(String name, boolean builtin, JetModel.Group[] groups, double longest) {
		this.name = name;
		this.builtin = builtin;
		this.groups = groups;
		this.longest = longest;
	}

	public static LoadedModel builtin() {
		return new LoadedModel(ModelManager.BUILTIN, true, JetModel.groups, JetConfig.MODEL_LENGTH);
	}
}
