package dev.stella.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stella.client.StellaClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/** Stella's custom render pipelines / layers (1.21.11 GPU pipeline API). */
public final class StellaPipelines {
    private StellaPipelines() {}

    private static Identifier id(String path) { return Identifier.of(StellaClient.MOD_ID, "pipeline/" + path); }

    /** Lines drawn through walls (ESP outlines, tracers). */
    public static final RenderPipeline LINES_SEE_THROUGH = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(id("lines_see_through"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Additive soft glow drawn under tracer cores (neon look). */
    public static final RenderPipeline LINES_GLOW_SEE_THROUGH = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(id("lines_glow_see_through"))
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Translucent filled quads drawn through walls (ESP fills, chunk squares). */
    public static final RenderPipeline QUADS_SEE_THROUGH = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(id("quads_see_through"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Additive (light-emitting) quads drawn through walls: beam halos and glow. */
    public static final RenderPipeline QUADS_GLOW_SEE_THROUGH = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(id("quads_glow_see_through"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Sky dome: depth-tested against terrain but never writes depth. */
    public static final RenderPipeline SKY_QUADS = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(id("sky_quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    /** Additive glow (nebula, aurora) for the sky. */
    public static final RenderPipeline SKY_GLOW_TRIANGLES = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(id("sky_glow_triangles"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static final RenderPipeline SKY_GLOW_QUADS = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(id("sky_glow_quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.LIGHTNING)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    public static final RenderLayer LINES = RenderLayer.of("stella_lines", RenderSetup.builder(LINES_SEE_THROUGH).expectedBufferSize(1 << 16).build());
    public static final RenderLayer GLOW = RenderLayer.of("stella_glow", RenderSetup.builder(LINES_GLOW_SEE_THROUGH).expectedBufferSize(1 << 16).build());
    public static final RenderLayer FILLS_GLOW = RenderLayer.of("stella_fills_glow", RenderSetup.builder(QUADS_GLOW_SEE_THROUGH).translucent().expectedBufferSize(1 << 17).build());
    public static final RenderLayer FILLS = RenderLayer.of("stella_fills", RenderSetup.builder(QUADS_SEE_THROUGH).translucent().expectedBufferSize(1 << 16).build());
    public static final RenderLayer SKY = RenderLayer.of("stella_sky", RenderSetup.builder(SKY_QUADS).expectedBufferSize(1 << 16).build());
    public static final RenderLayer SKY_GLOW = RenderLayer.of("stella_sky_glow", RenderSetup.builder(SKY_GLOW_TRIANGLES).expectedBufferSize(1 << 15).build());
    public static final RenderLayer SKY_GLOW_Q = RenderLayer.of("stella_sky_glow_q", RenderSetup.builder(SKY_GLOW_QUADS).expectedBufferSize(1 << 15).build());
}
