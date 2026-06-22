# 前端构建产物目录

Vue 3 构建产物放入此目录（CI 自动拷贝，结构如下）：

```
html/
├── index.html
└── static/
    ├── assets/
    └── ...
```

构建命令（前端项目根目录）：

```bash
npm run build    # 产物默认在 dist/
# 拷贝到部署包：
cp -r dist/* deploy/nginx/html/
```

未放置时，访问 Web 首页会 404，但后端 API（/api/）不受影响。
