package com.cesar.magicandsorcery.client.render;

import java.lang.reflect.Method;

/**
 * Compatibility utility for Oculus / Iris shaders.
 * Detects shader pipeline passes (e.g. shadow pass, first-person hand pass)
 * to prevent world rendering effects from being erroneously rendered inside the hand viewport.
 */
public class ShaderCompatHelper {
    private static boolean initialized = false;
    private static boolean irisPresent = false;

    private static Object irisApiInstance = null;
    private static Method isShaderPackInUseMethod = null;
    private static Method isRenderingShadowPassMethod = null;

    private static Method getPipelineManagerMethod = null;
    private static Method getPipelineNullableMethod = null;
    private static Method getPhaseMethod = null;

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstanceMethod = irisApiClass.getMethod("getInstance");
            irisApiInstance = getInstanceMethod.invoke(null);
            isShaderPackInUseMethod = irisApiClass.getMethod("isShaderPackInUse");
            isRenderingShadowPassMethod = irisApiClass.getMethod("isRenderingShadowPass");

            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");
            Class<?> pipelineManagerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            getPipelineNullableMethod = pipelineManagerClass.getMethod("getPipelineNullable");
            Class<?> wrpClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline");
            getPhaseMethod = wrpClass.getMethod("getPhase");

            irisPresent = true;
        } catch (Throwable t) {
            irisPresent = false;
        }
    }

    /**
     * Returns true if Oculus/Iris shaders are active and currently rendering a shadow pass
     * or first-person hand pass. Rendering custom world geometry during these passes causes
     * visual duplication (e.g., mini versions of the spell appearing next to the crosshair / hand).
     */
    public static boolean isInvalidRenderPass() {
        if (!initialized) {
            init();
        }
        if (!irisPresent || irisApiInstance == null) {
            return false;
        }

        try {
            boolean inUse = (boolean) isShaderPackInUseMethod.invoke(irisApiInstance);
            if (!inUse) {
                return false;
            }

            boolean isShadow = (boolean) isRenderingShadowPassMethod.invoke(irisApiInstance);
            if (isShadow) {
                return true;
            }

            if (getPipelineManagerMethod != null && getPipelineNullableMethod != null && getPhaseMethod != null) {
                Object pipelineMgr = getPipelineManagerMethod.invoke(null);
                if (pipelineMgr != null) {
                    Object pipeline = getPipelineNullableMethod.invoke(pipelineMgr);
                    if (pipeline != null) {
                        Object phase = getPhaseMethod.invoke(pipeline);
                        if (phase != null) {
                            String name = phase.toString();
                            if (name.contains("HAND") || name.contains("SHADOW") || name.contains("DESTROY")) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
