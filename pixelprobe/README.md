# Pixel Probe

Pixel Probe is a library to read pixel data and meta data from various image formats.

The currently supported image formats are:
- PNG
- JPEG
- Photoshop

# Generated source

The `generated` directory contains source code generated using the `chunkio-processor`
annotation processor. This code is generated from the annotations provided by the
`chunkio` support library.

Changing the implementation of `PsdDecoder` requires regeneration of this source code.
