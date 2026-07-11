#version 120

uniform vec2 u_size;
uniform float u_radius;
uniform vec4 u_color;

void main() {
    vec2 tex = gl_TexCoord[0].st;
    // Soft SDF rounded box in local UV space (OpenMyau-style).
    float dist = length(max((abs(tex - 0.5) + 0.5) * u_size - u_size + u_radius, 0.0))
            - u_radius + 0.5;
    float alpha = u_color.a * smoothstep(1.0, 0.0, dist);
    gl_FragColor = vec4(u_color.rgb, alpha);
}
