#version 330

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

// Positions are already in clip space: a full-screen quad.
void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
