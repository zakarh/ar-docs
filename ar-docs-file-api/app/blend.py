import os
import sys
import traceback

import bpy

debug_mode = False

"""
NOTE: This program is to be executed using the version of
Python that comes packaged with the Blender program.
"""


def main():
    """
    This function procedurally maps a source image to
    a 3D model in Blender which is then exported as an
    OBJ rendered file and saved to a target directory.
    """
    try:
        if debug_mode:
            print(os.getcwd())

        # Change directory
        # os.chdir("../../")

        if debug_mode:
            print(os.getcwd())
            print(sys.argv)

        # Extract Arguments:
        src = sys.argv[-5]
        image_file_name = sys.argv[-4]
        height = int(sys.argv[-3])
        width = int(sys.argv[-2])
        channels = int(sys.argv[-1])

        # Define Conversion Unit:
        px_per_mm = 392

        # Define name of Blender elements:
        scene_name = "Scene"  # Default scene name.
        mesh_name = "Cube"
        object_name = "Cube"

        # Retrieve elements from Blender:
        scene = bpy.data.scenes[scene_name]
        obj = bpy.data.objects[object_name]

        # Define Scene settings:
        # Scale to use when converting between blender units and dimensions.
        scale_length = 1
        system_units = "METRIC"  # The unit system to use for button display.
        # Unit to use for displaying/editing rotation values.
        system_rotation_units = "DEGREES"

        # Set scene properties.
        scene.unit_settings.scale_length = scale_length
        scene.unit_settings.system = system_units
        scene.unit_settings.system_rotation = system_rotation_units

        # Define Object Dimensions:
        x_d = width//px_per_mm
        y_d = height//px_per_mm
        z_d = 0.050

        # Define Object Rotations:
        x_radians = 0.0
        y_radians = 0.0
        z_radians = 1.5708  # 90 degrees.
        z_radians = 4.7123890  # 270 degrees.

        # Set dimensions and rotations of object:
        obj.dimensions = (x_d, y_d, z_d)
        obj.rotation_euler = (
            x_radians, y_radians, z_radians)

        # Create a material.
        # Add the image as the materials texture.
        # Add material to object.
        mat = bpy.data.materials.new(name="Image_Material")
        image_texture = bpy.data.textures.new("MP", "IMAGE")
        image = bpy.data.images.load(
            filepath="{}/{}".format(src, image_file_name))
        image_texture.image = image
        mat.texture_slots.add().texture = image_texture
        obj.data.materials[0] = mat

        # Create UV MAP:
        obj.data.uv_textures.new()
        obj.data.uv_textures["UVMap"].name = "Dom2_map"
        bpy.data.materials["Image_Material"].texture_slots[0].texture_coords = "UV"
        bpy.data.materials["Image_Material"].texture_slots[0].uv_layer = "Dom2_map"

        # Set the viewport shading.
        area = next(
            area for area in bpy.context.screen.areas if area.type == 'VIEW_3D')
        space = next(space for space in area.spaces if space.type == 'VIEW_3D')
        space.viewport_shade = 'RENDERED'

        # Export 3D model as an OBJ file:
        name, _ = image_file_name.split(".")
        export_file_path = "{}/{}.obj".format(src, name)
        bpy.ops.export_scene.obj(
            filepath=export_file_path, check_existing=True, axis_forward='X', axis_up='Y')
    except Exception:
        traceback.print_exc()


if __name__ == "__main__":
    main()
