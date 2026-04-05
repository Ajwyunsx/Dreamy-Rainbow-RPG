package com.hatkid.mkxpz;

import android.content.Intent;

final class ChapterLaunchConfig
{
    static final String EXTRA_GAME_SUBDIR = "com.hatkid.mkxpz.GAME_SUBDIR";
    static final String DEFAULT_INSTALL_DIR = "game";

    static final ChapterDefinition[] CHAPTERS = new ChapterDefinition[] {
        new ChapterDefinition("1", "game", R.string.chapter_option_1),
        new ChapterDefinition("2", "game-2", R.string.chapter_option_2),
        new ChapterDefinition("3", "game-3", R.string.chapter_option_3),
        new ChapterDefinition("4", "game-4", R.string.chapter_option_4),
        new ChapterDefinition("END", "game-end", R.string.chapter_option_end)
    };

    private ChapterLaunchConfig()
    {
    }

    static String resolveInstallDir(String chapterId)
    {
        for (ChapterDefinition chapter : CHAPTERS) {
            if (chapter.id.equalsIgnoreCase(chapterId)) {
                return chapter.installDir;
            }
        }

        return DEFAULT_INSTALL_DIR;
    }

    static String sanitizeInstallDir(String installDir)
    {
        if (installDir == null) {
            return DEFAULT_INSTALL_DIR;
        }

        for (ChapterDefinition chapter : CHAPTERS) {
            if (chapter.installDir.equals(installDir)) {
                return installDir;
            }
        }

        return DEFAULT_INSTALL_DIR;
    }

    static void applyToIntent(Intent intent, String chapterId)
    {
        intent.putExtra(EXTRA_GAME_SUBDIR, resolveInstallDir(chapterId));
    }

    static final class ChapterDefinition
    {
        final String id;
        final String installDir;
        final int labelResId;

        ChapterDefinition(String id, String installDir, int labelResId)
        {
            this.id = id;
            this.installDir = installDir;
            this.labelResId = labelResId;
        }
    }
}
