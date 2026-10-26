package com.chocodanette.legacycrafting;

import com.chocodanette.legacycrafting.workbench.WorkbenchItems;
import com.chocodanette.legacycrafting.workbench.WorkbenchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, LegacyCrafting.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
      DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LegacyCrafting.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> WORKBENCH =
      MENUS.register("workbench", () -> IMenuTypeExtension.create(WorkbenchMenu::new));

    /**
     * The items left in the workbenches of a chunk.
     */
    public static final Supplier<AttachmentType<WorkbenchItems>> WORKBENCH_ITEMS = ATTACHMENTS.register("workbench_items",
      () -> AttachmentType.builder(WorkbenchItems::new).serialize(WorkbenchItems.CODEC).build());

    private ModMenus()
    {
    }
}
