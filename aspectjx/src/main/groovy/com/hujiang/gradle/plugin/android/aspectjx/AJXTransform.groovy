/*
 * Copyright 2018 firefly1126, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.gradle_plugin_android_aspectjx
 */
package com.hujiang.gradle.plugin.android.aspectjx

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.google.common.collect.ImmutableSet
import com.hujiang.gradle.plugin.android.aspectjx.internal.cache.VariantCache
import com.hujiang.gradle.plugin.android.aspectjx.internal.procedure.*
import groovy.util.logging.Slf4j
import org.gradle.api.Project

/**
 * class description here
 * https://www.jianshu.com/p/37a5e058830a%20
 * https://blog.csdn.net/isJoker/article/details/103399811#:~:text=Gradle%20Transform%E6%A6%82%E8%BF%B0,Gradle%20Transform%E6%98%AFAndroid%E5%AE%98%E6%96%B9%E6%8F%90%E4%BE%9B%E7%BB%99%E5%BC%80%E5%8F%91%E8%80%85%E5%9C%A8%E9%A1%B9%E7%9B%AE%E6%9E%84%E5%BB%BA%E9%98%B6%E6%AE%B5%EF%BC%88.class%20-%3E.dex%E8%BD%AC%E6%8D%A2%E6%9C%9F%E9%97%B4%EF%BC%89%E7%94%A8%E6%9D%A5%E4%BF%AE%E6%94%B9.class%E6%96%87%E4%BB%B6%E7%9A%84%E4%B8%80%E5%A5%97%E6%A0%87%E5%87%86API%EF%BC%8C%E5%8D%B3%E6%8A%8A%E8%BE%93%E5%85%A5%E7%9A%84.class%E6%96%87%E4%BB%B6%E8%BD%AC%E5%8F%98%E6%88%90%E7%9B%AE%E6%A0%87%E5%AD%97%E8%8A%82%E7%A0%81%E6%96%87%E4%BB%B6%E3%80%82
 * @author simon* @version 1.0.0* @since 2018-03-12
 */
@Slf4j
class AJXTransform extends Transform {
    private String TAG = "AJXTransform"

    AJXProcedure ajxProcedure

    AJXTransform(Project proj) {
        ajxProcedure = new AJXProcedure(proj)
        log.error("AJXTransform" + proj)
    }

    @Override
    String getName() {
        return "ajx"
    }

    /**
     * 需要处理的数据类型，有两种枚举类型

     CLASSES
     代表处理的 java 的 class 文件，返回TransformManager.CONTENT_CLASS

     RESOURCES
     代表要处理 java 的资源，返回TransformManager.CONTENT_RESOURCES
     * @return
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.<QualifiedContent.ContentType> of(QualifiedContent.DefaultContentType.CLASSES)
    }

    /**
     * 指 Transform 要操作内容的范围，官方文档 Scope 有 7 种类型：
     EXTERNAL_LIBRARIES ----------------只有外部库
     PROJECT -----------------------------------只有项目内容
     PROJECT_LOCAL_DEPS ------------- 只有项目的本地依赖(本地jar)
     PROVIDED_ONLY ------------------------只提供本地或远程依赖项
     SUB_PROJECTS -------------------------只有子项目
     SUB_PROJECTS_LOCAL_DEPS-----只有子项目的本地依赖项(本地jar)
     TESTED_CODE ---------------------------由当前变量(包括依赖项)测试的代码
     如果要处理所有的class字节码，返回TransformManager.SCOPE_FULL_PROJECT
     * @return
     */
    //TODO 换回SCOPE_FULL_PROJECT
    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.PROJECT_ONLY
    }

    /**
     * 增量编译开关

     当我们开启增量编译的时候，相当input包含了changed/removed/added三种状态，实际上还有notchanged。需要做的操作如下：

     NOTCHANGED: 当前文件不需处理，甚至复制操作都不用；
     ADDED、CHANGED: 正常处理，输出给下一个任务；
     REMOVED: 移除outputProvider获取路径对应的文件。
     * @return
     */
    @Override
    boolean isIncremental() {
        //是否支持增量编译
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {

        Project project = ajxProcedure.project

        String transformTaskVariantName = transformInvocation.context.getVariantName()
        log.error("AJXTransform transformTaskVariantName" + transformTaskVariantName)
        VariantCache variantCache = new VariantCache(ajxProcedure.project, ajxProcedure.ajxCache, transformTaskVariantName)

        ajxProcedure.with(new CheckAspectJXEnableProcedure(project, variantCache, transformInvocation))
        log.error("AJXTransform incremental" + transformInvocation.incremental)

        if (transformInvocation.incremental) {
            //incremental build
            ajxProcedure.with(new UpdateAspectFilesProcedure(project, variantCache, transformInvocation))
            ajxProcedure.with(new UpdateInputFilesProcedure(project, variantCache, transformInvocation))
            ajxProcedure.with(new UpdateAspectOutputProcedure(project, variantCache, transformInvocation))
        } else {
            //delete output and cache before full build
            transformInvocation.outputProvider.deleteAll()
            variantCache.reset()

            ajxProcedure.with(new CacheAspectFilesProcedure(project, variantCache, transformInvocation))
            ajxProcedure.with(new CacheInputFilesProcedure(project, variantCache, transformInvocation))
            ajxProcedure.with(new DoAspectWorkProcedure(project, variantCache, transformInvocation))
        }

        ajxProcedure.with(new OnFinishedProcedure(project, variantCache, transformInvocation))

        ajxProcedure.doWorkContinuously()
    }
}
