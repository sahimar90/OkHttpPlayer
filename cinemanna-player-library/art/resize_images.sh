#!/bin/bash
androidProjectResPath="../src/main/res"

imageExtension="png" # or "JPEG" or "png"

name="subtitle_on12"
imgName="$name.$imageExtension"		# the destination image name
imgPath="icons/$name.$imageExtension"	# Inkscape output



#	mipmap-mdpi
echo "mipmap-mdpi"
/opt/ImageMagick/bin/convert $imgPath -resize 36x36 $imgName
mv $imgName "$androidProjectResPath/mipmap-mdpi"


#	mipmap-hdpi
echo "mipmap-hdpi"
/opt/ImageMagick/bin/convert $imgPath -resize 48x48 $imgName
mv $imgName "$androidProjectResPath/mipmap-hdpi"

#	mipmap-xhdpi
echo "mipmap-xhdpi"
/opt/ImageMagick/bin/convert $imgPath -resize 64x64 $imgName
mv $imgName "$androidProjectResPath/mipmap-xhdpi"

#	mipmap-xxhdpi
echo "mipmap-xxhdpi"
/opt/ImageMagick/bin/convert $imgPath -resize 72x72 $imgName
mv $imgName "$androidProjectResPath/mipmap-xxhdpi"
