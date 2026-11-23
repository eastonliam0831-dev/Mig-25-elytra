package com.mig25.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Safety net: each mixin is only applied if its target method really exists with the expected
 * signature in this Minecraft version. A missing target means the feature is skipped instead of crashing.
 */
public class Mig25MixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	private static boolean has(ClassNode cn, String name, String descContains) {
		for (MethodNode m : cn.methods) {
			if (m.name.equals(name) && m.desc.contains(descContains)) return true;
		}
		return false;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		try {
			ClassNode cn = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
			boolean ok = true;
			if (mixinClassName.endsWith("EntityRenderDispatcherMixin")) ok = has(cn, "shouldRender", "DDDF)Z");
			else if (mixinClassName.endsWith("EntityTurnMixin")) ok = has(cn, "turn", "(DD)V");
			else if (mixinClassName.endsWith("CameraMixin")) ok = has(cn, "setRotation", "(FFF)V");
			if (!ok) System.err.println("[mig25] skipping " + mixinClassName + ": target method not found");
			return ok;
		} catch (Throwable t) {
			System.err.println("[mig25] skipping " + mixinClassName + ": " + t);
			return false;
		}
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
