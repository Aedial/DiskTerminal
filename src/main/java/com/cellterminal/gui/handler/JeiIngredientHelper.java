package com.cellterminal.gui.handler;

import java.lang.reflect.Field;

import javax.annotation.Nullable;


/**
 * Shared compatibility helpers for JEI and HEI ingredient objects.
 */
public final class JeiIngredientHelper {

    private static volatile Field bookmarkItemIngredientField;
    private static volatile Class<?> bookmarkItemClass;
    private static volatile boolean bookmarkLookupAttempted;

    private JeiIngredientHelper() {}

    /**
     * Latest HEI wraps bookmarked ingredients in BookmarkItem instances instead of
     * returning the raw ingredient directly.
     */
    @Nullable
    public static Object unwrapBookmarkItem(@Nullable Object ingredient) {
        if (ingredient == null) return null;

        if (!bookmarkLookupAttempted) {
            try {
                bookmarkItemClass = Class.forName("mezz.jei.bookmarks.BookmarkItem");
            } catch (ClassNotFoundException ignored) {
                // Vanilla JEI and older HEI return raw ingredients directly.
            }

            bookmarkLookupAttempted = true;
        }

        Class<?> wrapperClass = bookmarkItemClass;
        if (wrapperClass == null || !wrapperClass.isInstance(ingredient)) return ingredient;

        try {
            Field field = bookmarkItemIngredientField;
            if (field == null) {
                field = wrapperClass.getField("ingredient");
                bookmarkItemIngredientField = field;
            }

            Object innerIngredient = field.get(ingredient);
            if (innerIngredient != null) return innerIngredient;
        } catch (ReflectiveOperationException ignored) {
            // Preserve the original object if the wrapper layout changes.
        }

        return ingredient;
    }
}