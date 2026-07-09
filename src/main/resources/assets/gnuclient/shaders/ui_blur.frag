#version 120

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform vec2 BlurDir;
uniform float Radius;

varying vec2 vTexCoord;

void main() {
    vec2 texel = BlurDir / InSize;
    float r = max(Radius, 1.0);
    vec4 sum = texture2D(DiffuseSampler, vTexCoord) * 0.2270270270;
    sum += texture2D(DiffuseSampler, vTexCoord + texel * 1.3846153846) * 0.3162162162;
    sum += texture2D(DiffuseSampler, vTexCoord - texel * 1.3846153846) * 0.3162162162;
    sum += texture2D(DiffuseSampler, vTexCoord + texel * 3.2307692308) * 0.0702702703;
    sum += texture2D(DiffuseSampler, vTexCoord - texel * 3.2307692308) * 0.0702702703;
    gl_FragColor = vec4(sum.rgb, 1.0);
}
