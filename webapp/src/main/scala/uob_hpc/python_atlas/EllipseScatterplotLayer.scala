package uob_hpc.python_atlas

import deckgl.*

import scala.scalajs.js

trait EllipseScatterplotLayerProps[A] extends ScatterplotLayerProps[A] {
  val getRadiusRatio: ValueOrOpt[A, Double] = js.undefined
}

class EllipseScatterplotLayer[A](prop: EllipseScatterplotLayerProps[A]) extends ScatterplotLayer[A](prop) {

  override val componentName: String = "EllipseScatterplotLayer"
  var defaultProps: js.Object = js.Dynamic.literal(getRadiusRatio = js.Dynamic.literal(`type` = "accessor", value = 1))

  override def initializeState(): Unit = {
    super.initializeState()
    this
      .asInstanceOf[js.Dynamic]
      .state
      .attributeManager
      .addInstanced(
        js.Dynamic.literal(
          instanceRadiusRatio = js.Dynamic.literal(size = 1, accessor = "getRadiusRatio")
        )
      )
  }
  //  override def draw(models: LayerDrawOptions): LayerDrawOptions = {
  //    console.log(models)
  //    new LayerDrawOptions {
  //      override val uniforms: Object = js.Object.assign(js.Object(), EllipseScatterplotLayer.super.draw(models))
  //    }
  //  }

  override def getShaders(): js.Object = js.Object.assign(
    js.Object(),        //
    super.getShaders(), //
    js.Dynamic.literal(
      vs = """
          |#define SHADER_NAME ellipse-scatterplot-layer-vertex-shader
          |
          |attribute vec3 positions;
          |
          |attribute vec3 instancePositions;
          |attribute vec3 instancePositions64Low;
          |attribute float instanceRadius;
          |attribute float instanceLineWidths;
          |attribute vec4 instanceFillColors;
          |attribute vec4 instanceLineColors;
          |attribute vec3 instancePickingColors;
          |
          |uniform float opacity;
          |uniform float radiusScale;
          |uniform float radiusMinPixels;
          |uniform float radiusMaxPixels;
          |uniform float lineWidthScale;
          |uniform float lineWidthMinPixels;
          |uniform float lineWidthMaxPixels;
          |uniform float stroked;
          |uniform bool filled;
          |uniform bool antialiasing;
          |uniform bool billboard;
          |uniform int radiusUnits;
          |uniform int lineWidthUnits;
          |
          |varying vec4 vFillColor;
          |varying vec4 vLineColor;
          |varying vec2 unitPosition;
          |varying float innerUnitRadius;
          |varying float outerRadiusPixels;
          |
          |attribute float instanceRadiusRatio;
          |varying float radiusRatio;
          |
          |
          |void main(void) {
          |  geometry.worldPosition = instancePositions;
          |
          |  // Multiply out radius and clamp to limits
          |  outerRadiusPixels = clamp(
          |    project_size_to_pixel(radiusScale * instanceRadius  , radiusUnits),
          |    radiusMinPixels, radiusMaxPixels
          |  );
          |
          |
          |  // Multiply out line width and clamp to limits
          |  float lineWidthPixels = clamp(
          |    project_size_to_pixel(lineWidthScale * instanceLineWidths, lineWidthUnits),
          |    lineWidthMinPixels, lineWidthMaxPixels
          |  );
          |
          |  // outer radius needs to offset by half stroke width
          |  outerRadiusPixels += stroked * lineWidthPixels / 2.0;
          |
          |  radiusRatio = instanceRadiusRatio;
          |
          |  // Expand geometry to accomodate edge smoothing
          |  float edgePadding = antialiasing ? (outerRadiusPixels + SMOOTH_EDGE_RADIUS) / outerRadiusPixels : 1.0;
          |
          |  // position on the containing square in [-1, 1] space
          |  unitPosition = edgePadding * positions.xy;
          |  geometry.uv = unitPosition;
          |  geometry.pickingColor = instancePickingColors;
          |
          |  innerUnitRadius = 1.0 - stroked * lineWidthPixels / outerRadiusPixels;
          |
          |  if (billboard) {
          |    gl_Position = project_position_to_clipspace(instancePositions, instancePositions64Low, vec3(0.0), geometry.position);
          |    vec3 offset = edgePadding * positions * outerRadiusPixels;
          |    DECKGL_FILTER_SIZE(offset, geometry);
          |    gl_Position.xy += project_pixel_size_to_clipspace(offset.xy);
          |  } else {
          |    vec3 offset = edgePadding * positions * project_pixel_size(outerRadiusPixels);
          |    DECKGL_FILTER_SIZE(offset, geometry);
          |    gl_Position = project_position_to_clipspace(instancePositions, instancePositions64Low, offset, geometry.position);
          |  }
          |
          |  DECKGL_FILTER_GL_POSITION(gl_Position, geometry);
          |
          |  // Apply opacity to instance color, or return instance picking color
          |  vFillColor = vec4(instanceFillColors.rgb, instanceFillColors.a * opacity);
          |  DECKGL_FILTER_COLOR(vFillColor, geometry);
          |  vLineColor = vec4(instanceLineColors.rgb, instanceLineColors.a * opacity);
          |  DECKGL_FILTER_COLOR(vLineColor, geometry);
          |}
          |""".stripMargin,
      fs = """
          |#define SHADER_NAME ellipse-scatterplot-layer-fragment-shader
          |
          |precision highp float;
          |
          |uniform bool filled;
          |uniform float stroked;
          |uniform bool antialiasing;
          |
          |varying vec4 vFillColor;
          |varying vec4 vLineColor;
          |varying vec2 unitPosition;
          |varying float innerUnitRadius;
          |varying float outerRadiusPixels;
          |
          |varying float radiusRatio;
          |
          |void main(void) {
          |  geometry.uv = unitPosition;
          |
          |  float distToCenter = length(unitPosition * vec2(1.0, radiusRatio)) * outerRadiusPixels;
          |  float inCircle = antialiasing ?
          |    smoothedge(distToCenter, outerRadiusPixels) :
          |    step(distToCenter, outerRadiusPixels); // distToCenter > outerRadiusPixels ? 0 : 1
          |
          |  if (inCircle == 0.0) {
          |     discard;
          |  }
          |
          |  if (stroked > 0.5) {
          |    float isLine = antialiasing ?
          |      smoothedge(innerUnitRadius * outerRadiusPixels, distToCenter) :
          |      step(innerUnitRadius * outerRadiusPixels, distToCenter);
          |
          |    if (filled) {
          |      gl_FragColor = mix(vFillColor, vLineColor, isLine);
          |    } else {
          |      if (isLine == 0.0) {
          |        discard;
          |      }
          |      gl_FragColor = vec4(vLineColor.rgb, vLineColor.a * isLine);
          |    }
          |  } else if (filled) {
          |    gl_FragColor = vFillColor;
          |  } else {
          |    discard;
          |  }
          |  gl_FragColor.a *= inCircle;
          |  DECKGL_FILTER_COLOR(gl_FragColor, geometry);
          |}
          |""".stripMargin
    )
  )
}
