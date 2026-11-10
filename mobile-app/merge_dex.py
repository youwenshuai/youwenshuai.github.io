import sys, zipfile, shutil

src, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]
zin = zipfile.ZipFile(src, "r")
zout = zipfile.ZipFile(out, "w")
for item in zin.infolist():
    data = zin.read(item.filename)
    zi = zipfile.ZipInfo(item.filename, date_time=item.date_time)
    zi.compress_type = item.compress_type   # 保持原压缩方式（resources.arsc 保持 STORED）
    zi.external_attr = item.external_attr
    zout.writestr(zi, data)
with open(dex, "rb") as f:
    zout.writestr("classes.dex", f.read(), zipfile.ZIP_DEFLATED)
zin.close()
zout.close()
print("merged:", out)
