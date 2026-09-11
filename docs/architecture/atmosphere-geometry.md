# Atmosphere geometry

The generative layer behind Home and Conversation. It is a designed
mathematical system, not a generated one.

## Every visible line comes from an equation

`GeometryTopology` builds a scene from `MathematicalCurve`s, each one a
parametric equation evaluated over its own domain: circle, ellipse, rose
(`r = a·cos(kt + φ)`), Lissajous, epitrochoid/hypotrochoid. There are no
arbitrary control points, no node-to-node edges and no random polylines — the
previous system had all three, which is why it read as rough rather than
engineered.

A curve's `tStart`/`tEnd` are how incompleteness is expressed: an arc that stops
short is a decision made in the equation's own domain, not a clipped drawing.

## Curated compositions, not one random equation

`CompositionFamily` fixes *what a scene is* — ORBITAL_I, ORBITAL_II, ROSE_FIELD,
LISSAJOUS_FIELD, TROCHOID_FIELD, ECLIPSE — while parameters vary per seed within
curated limits. Selection is weighted toward the strictly orbital families
(`ORBITAL_WEIGHT`), because the target is an instrument or orbital diagram, not a
plot.

Randomness chooses the family, its parameters, rotation and phase. It never
chooses coordinates.

## Nodes are derived, never scattered

Nodes mark curve intersections, the points where deliberately incomplete arcs
stop, and per-curve extrema — in that priority, capped at six. A node always sits
on geometry the scene actually contains.

## Signals travel the equation

A pulse moves along a curve's own parameter domain and blooms as it passes
(`EquationSignalState.intensity`), rather than a whole line flashing or a point
jumping between unrelated places.

## The camera is why nothing stretches

Geometry is authored once in a world where both axes mean the same thing and a
circle is a circle. Home and Conversation are two **cameras** onto that one
world, not two geometries and not two sizes of one.

`GeometryCamera.scaleFor` is derived from viewport **width alone** and is used
for both axes. The viewport's height then decides only how much of the world is
visible, never how anything is shaped.

This is the fix for a specific, visible defect: the previous renderer projected
`x * sceneScale` against `y * height`, so every circle became an ellipse whose
eccentricity depended on the height of its container — and Home's hero and
Conversation's canvas are very different heights, so entering a conversation
visibly stretched the scene.

- **Home** is a concentrated crop through a short hero; rings running off the
  edges are intended.
- **Conversation** is the same scene, *closer* and spanning the full screen —
  entering the structure. Pulling back instead would have shrunk the scene into
  the middle of a tall canvas.
- `GeometryCamera.framing` centres the camera on the scene's actual drawn bounds,
  so asymmetric compositions sit deliberately on every seed.
- Entering a conversation animates `GeometryCamera.lerp` from the Home framing to
  the Conversation one. Every intermediate frame is a camera, so every
  intermediate frame is uniform.

## Rendering cost

Paths are built once per topology in **world coordinates** and never rebuilt —
not per frame, not per scroll. The camera is applied at draw time as a single
`translate`/`scale(s, s)`/`translate`, with stroke widths and radii divided by
that scale so they stay in dp. Animating the camera therefore costs nothing and
structurally cannot deform a path: there is one scale value, so there is no
second axis to disagree with it.

Per frame, only signal progress and opacity are evaluated.

## Offline

Offline keeps the scene exactly: same composition, same parameters, same nodes.
Only the colour drains to graphite and the firing stops. Reconnecting relights
the same geometry.
