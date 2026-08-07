@echo off
set MINIO_ROOT_USER=ivdadmin
set MINIO_ROOT_PASSWORD=Minio@123456
D:\IVD\middleware\minio\minio.exe server D:\IVD\middleware\minio\data --address :9000 --console-address :9001
