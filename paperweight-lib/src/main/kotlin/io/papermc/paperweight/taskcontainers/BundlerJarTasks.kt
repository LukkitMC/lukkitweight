/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2023 Kyle Wood (DenWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.paperweight.taskcontainers

import com.google.gson.JsonObject
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.util.*
import io.papermc.paperweight.util.constants.*
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*

@Suppress("MemberVisibilityCanBePrivate")
class BundlerJarTasks(
    project: Project,
    private val bundlerJarName: Provider<String>,
    private val mainClassString: Provider<String>,
) {
    val createBundlerJar: TaskProvider<CreateBundlerJar>
    val createLukkitclipJar: TaskProvider<CreatePaperclipJar>

    val createReobfBundlerJar: TaskProvider<CreateBundlerJar>
    val createReobfLukkitclipJar: TaskProvider<CreatePaperclipJar>

    init {
        val (createBundlerJar, createLukkitclipJar) = project.createBundlerJarTask("mojmap")
        val (createReobfBundlerJar, createReobfLukkitclipJar) = project.createBundlerJarTask("reobf")
        this.createBundlerJar = createBundlerJar
        this.createLukkitclipJar = createLukkitclipJar

        this.createReobfBundlerJar = createReobfBundlerJar
        this.createReobfLukkitclipJar = createReobfLukkitclipJar
    }

    fun configureBundlerTasks(
        bundlerVersionJson: Provider<RegularFile>,
        serverLibrariesList: Provider<RegularFile>,
        vanillaJar: Provider<RegularFile>,
        mojangJar: Provider<RegularFile>,
        reobfJar: TaskProvider<RemapJar>,
        mcVersion: Provider<String>
    ) {
        createBundlerJar.configureWith(
            bundlerVersionJson,
            serverLibrariesList,
            vanillaJar,
            mojangJar,
        )
        createReobfBundlerJar.configureWith(
            bundlerVersionJson,
            serverLibrariesList,
            vanillaJar,
            reobfJar.flatMap { it.outputJar },
        )

        createLukkitclipJar.configureWith(vanillaJar, createBundlerJar, mcVersion)
        createReobfLukkitclipJar.configureWith(vanillaJar, createReobfBundlerJar, mcVersion)
    }

    private fun Project.createBundlerJarTask(
        classifier: String = "",
    ): Pair<TaskProvider<CreateBundlerJar>, TaskProvider<CreatePaperclipJar>> {
        val bundlerTaskName = "create${classifier.capitalized()}BundlerJar"
        val paperclipTaskName = "create${classifier.capitalized()}LukkitclipJar"

        val bundlerJarTask = tasks.register<CreateBundlerJar>(bundlerTaskName) {
            group = "paperweight"
            description = "Build a runnable bundler jar"

            paperclip.from(configurations.named(PAPERCLIP_CONFIG))
            mainClass.set(mainClassString)

            outputZip.set(layout.buildDirectory.file("libs/${jarName("bundler", classifier)}"))
        }
        val paperclipJarTask = tasks.register<CreatePaperclipJar>(paperclipTaskName) {
            group = "paperweight"
            description = "Build a runnable lukkitclip jar"

            libraryChangesJson.set(bundlerJarTask.flatMap { it.libraryChangesJson })
            outputZip.set(layout.buildDirectory.file("libs/${jarName("lukkitclip", classifier)}"))
        }
        return bundlerJarTask to paperclipJarTask
    }

    private fun Project.jarName(kind: String, classifier: String): String {
        return listOfNotNull(
            project.name,
            kind,
            project.version,
            classifier.takeIf { it.isNotBlank() },
        ).joinToString("-") + ".jar"
    }

    private fun TaskProvider<CreateBundlerJar>.configureWith(
        bundlerVersionJson: Provider<RegularFile>,
        serverLibrariesListFile: Provider<RegularFile>,
        vanillaJar: Provider<RegularFile>,
        serverJar: Provider<RegularFile>,
    ) = this {
        libraryArtifacts.set(project.configurations.named(SERVER_RUNTIME_CLASSPATH))
        serverLibrariesList.set(serverLibrariesListFile)
        vanillaBundlerJar.set(vanillaJar)

        versionArtifacts {
            registerVersionArtifact(
                bundlerJarName.get(),
                bundlerVersionJson,
                serverJar
            )
        }
    }

    private fun TaskProvider<CreatePaperclipJar>.configureWith(
        vanillaJar: Provider<RegularFile>,
        createBundlerJar: TaskProvider<CreateBundlerJar>,
        mcVers: Provider<String>
    ) = this {
        originalBundlerJar.set(vanillaJar)
        bundlerJar.set(createBundlerJar.flatMap { it.outputZip })
        mcVersion.set(mcVers)
    }

    companion object {
        fun NamedDomainObjectContainer<CreateBundlerJar.VersionArtifact>.registerVersionArtifact(
            name: String,
            versionJson: Provider<RegularFile>,
            serverJar: Provider<RegularFile>
        ) = register(name) {
            id.set(versionJson.map { gson.fromJson<JsonObject>(it)["id"].asString })
            file.set(serverJar)
        }
    }
}
