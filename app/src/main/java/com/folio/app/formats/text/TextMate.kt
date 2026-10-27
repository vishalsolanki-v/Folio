package com.folio.app.formats.text

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource

/** Sora's TextMate registries (process-wide singletons), loaded once from assets/textmate. */
object TextMate {
    const val LIGHT_THEME = "folio-light"
    const val DARK_THEME = "folio-dark"

    private val lock = Mutex()
    @Volatile private var loaded = false

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
                listOf(LIGHT_THEME to false, DARK_THEME to true).forEach { (name, dark) ->
                    val path = "textmate/$name.json"
                    ThemeRegistry.getInstance().loadTheme(
                        ThemeModel(IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null), name)
                            .apply { isDark = dark },
                    )
                }
                GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
            }
            loaded = true
        }
    }

    private val scopes: Map<String, String> = buildMap {
        fun scope(name: String, vararg exts: String) = exts.forEach { put(it, name) }
        scope("source.json", "json", "jsonc", "geojson")
        scope("source.yaml", "yaml", "yml")
        scope("source.ini", "ini", "cfg", "conf", "properties", "env", "toml")
        scope("source.shell", "sh", "bash", "zsh", "fish")
        scope("source.sql", "sql")
        scope("source.c", "c", "h")
        scope("source.cpp", "cpp", "cc", "cxx", "hpp", "hh")
        scope("source.cs", "cs")
        scope("source.go", "go")
        scope("source.rust", "rs")
        scope("source.java", "java")
        scope("source.kotlin", "kt", "kts")
        scope("source.python", "py")
        scope("source.js", "js", "mjs", "cjs", "jsx")
        scope("source.ts", "ts")
        scope("source.tsx", "tsx")
        scope("source.css", "css", "scss", "sass", "less")
        scope("text.html.basic", "html", "htm", "xhtml", "vue", "svelte")
        scope("text.xml", "xml", "plist", "svg")
        scope("text.html.markdown", "md", "markdown")
        scope("source.php", "php")
        scope("source.ruby", "rb")
        scope("source.swift", "swift")
        scope("source.dart", "dart")
        scope("source.dockerfile", "dockerfile")
        scope("source.diff", "diff", "patch")
        scope("source.lua", "lua")
    }

    /** Grammar scope for a file extension, or null for plain text. */
    fun scopeFor(extension: String): String? = scopes[extension.lowercase()]
}
