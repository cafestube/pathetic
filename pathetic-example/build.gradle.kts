/*
 * This file was generated by the Gradle 'init' task.
 *
 * This project uses @Incubating APIs which are subject to change.
 */

plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    api(project(":pathetic-mapping"))
    compileOnly(libs.org.spigotmc.spigot.api)
}

description = "PatheticTest"
