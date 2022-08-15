package com.demo;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class TimePlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        android.registerTransform(new TimeTransform());
    }
}
