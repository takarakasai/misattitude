package io.github.takarakasai.misattitude.domain

/**
 * Visual choice for the rotating body in the 3-D view.
 *
 *   Cube     — translucent reference cube; lets the user see the origin and the
 *              axes passing through the body.
 *   Capsule  — neutral robot-body shape (cylinder + hemispheres). A middle ground
 *              between the abstract cube and the very-specific character meshes.
 *   Teapot   — the Utah teapot, a graphics-history mascot. Built from primitives
 *              (squashed-sphere body + flat lid + knob + tilted-cone spout +
 *              torus-segment handle). Spout (front, +Z) and handle (back, -Z)
 *              give a pair of unmistakable directional cues — even after large
 *              rotations the user can immediately read the body's orientation.
 *   Spot     — Keenan Crane's "Spot" mascot cow (CC0 / public domain). Loaded from
 *              an OBJ asset; texture sampled per-vertex so the iconic black-and-
 *              white cow pattern bakes into vertex colors and renders with the
 *              same vertex_color material as the other shapes. Four legs, head
 *              and tail give the strongest directional cues of any shape here.
 */
enum class BodyShape { Cube, Capsule, Teapot, Spot }
