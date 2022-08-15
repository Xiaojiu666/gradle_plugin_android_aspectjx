package com.demo;


import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class TimeTransform extends Transform {


    @Override
    public String getName() {
        return "TimeTransform";
    }


    /**
     * 需要处理的数据类型，有两种枚举类型
     * <p>
     * CLASSES
     * 代表处理的 java 的 class 文件，返回TransformManager.CONTENT_CLASS
     * <p>
     * RESOURCES
     * 代表要处理 java 的资源，返回TransformManager.CONTENT_RESOURCES
     *
     * @return
     */
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.<QualifiedContent.ContentType>of(QualifiedContent.DefaultContentType.CLASSES);
    }


    /**
     * 指 Transform 要操作内容的范围，官方文档 Scope 有 7 种类型：
     * EXTERNAL_LIBRARIES ----------------只有外部库
     * PROJECT -----------------------------------只有项目内容
     * PROJECT_LOCAL_DEPS ------------- 只有项目的本地依赖(本地jar)
     * PROVIDED_ONLY ------------------------只提供本地或远程依赖项
     * SUB_PROJECTS -------------------------只有子项目
     * SUB_PROJECTS_LOCAL_DEPS-----只有子项目的本地依赖项(本地jar)
     * TESTED_CODE ---------------------------由当前变量(包括依赖项)测试的代码
     * 如果要处理所有的class字节码，返回TransformManager.SCOPE_FULL_PROJECT
     *
     * @return
     */
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }


    /**
     * https://juejin.cn/post/6844904150925312008
     */
    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        //删除旧的输出
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        outputProvider.deleteAll();
        //处理class
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        for (TransformInput transFromInput :
                inputs) {
            Collection<DirectoryInput> directoryInputs = transFromInput.getDirectoryInputs();
            for (DirectoryInput directoryInput :
                    directoryInputs) {
                handleDirectoryInput(directoryInput, outputProvider);
            }
            Collection<JarInput> jarInputs = transFromInput.getJarInputs();
            for (JarInput jarInput : jarInputs) {
                handleJarInput(jarInput, outputProvider);
            }
        }
    }

    /**
     * 处理目录下的class文件
     *
     * @param directoryInput
     * @param outputProvider
     */
    public void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider) throws IOException {
        File file = directoryInput.getFile();
        File[] files = file.listFiles();
        if (file.isDirectory()) {
            for (File subFile :
                    files) {
                String name = subFile.getName();
                if (isClassFile(name)) {
                    dealClassFile4AddTime(subFile);
                }
            }
        }
        File contentLocation = outputProvider.getContentLocation(directoryInput.getName(), directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
        FileUtils.copyDirectory(directoryInput.getFile(), contentLocation);
    }

    /**
     * 处理Jar中的class文件
     *
     * @param jarInput
     * @param outputProvider
     */
    public void handleJarInput(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        if (jarInput.getFile().getAbsolutePath().endsWith(".jar")) {
            String jarName = jarInput.getName();
            String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4);
            }
            JarFile jarFile = new JarFile(jarInput.getFile());
            Enumeration enumeration = jarFile.entries();
            File tempFile = new File(jarInput.getFile().getParent() + File.separator + "temp.jar");
            //避免上次的缓存被重复插入
            if (tempFile.exists()) {
                tempFile.delete();
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tempFile));
            //保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                InputStream inputStream = jarFile.getInputStream(zipEntry);
                if (isClassFile(entryName)) {
                    jarOutputStream.putNextEntry(zipEntry);
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                    ClassVisitor classVisitor = new ClassAdapterVisitor(classWriter);
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
                    byte[] bytes = classWriter.toByteArray();
                    jarOutputStream.write(bytes);
                } else {
                    jarOutputStream.putNextEntry(zipEntry);
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                }
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            jarFile.close();
            File dest = outputProvider.getContentLocation(jarName + "_" + md5Name, jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
            FileUtils.copyFile(tempFile, dest);
            tempFile.delete();
        }
    }

    /**
     * 判断是否为需要处理class文件
     *
     * @param name
     * @return
     */
    boolean isClassFile(String name) {
        return (name.endsWith(".class") && !name.startsWith("R")
                && "R.class" != name && "BuildConfig.class" != name);
    }


    public void dealClassFile4AddTime(File file) {
        /**
         * 2、执行分析与插桩
         */
        //class字节码的读取与分析引擎
        ClassReader cr = null;
        try {
            /**
             * 1、准备待分析的class
             */
            FileInputStream fis = new FileInputStream
                    (file);
            cr = new ClassReader(fis);
            // 写出器 COMPUTE_FRAMES 自动计算所有的内容，后续操作更简单
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            //分析，处理结果写入cw EXPAND_FRAMES：栈图以扩展格式进行访问
            cr.accept(new ClassAdapterVisitor(cw), ClassReader.EXPAND_FRAMES);

            /**
             * 3、获得结果并输出
             */
            byte[] newClassBytes = cw.toByteArray();
//            File file = new File("/Users/edz/StudioProjects/gradle_plugin_android_aspectjx/app/src/main/java/com/example/test2");
//            file.mkdirs();

            FileOutputStream fos = new FileOutputStream
                    (file.getParentFile().getAbsolutePath() + File.separator + file.getName());
            fos.write(newClassBytes);

            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}