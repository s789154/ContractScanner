# ContractScanner - 合同扫描器

通过 GitHub Actions 自动构建 APK，无需本地安装 Android Studio。

## 使用步骤

### 第一步：创建 GitHub 仓库

1. 打开 [GitHub](https://github.com) 并登录
2. 点击右上角 **+** → **New repository**
3. 仓库名填写 `ContractScanner`
4. 选择 **Public**（公开，免费）或 **Private**（私有，需 GitHub Pro）
5. 点击 **Create repository**

### 第二步：上传项目代码

**方法一：通过 GitHub 网页上传**

1. 进入刚创建的仓库页面
2. 点击 **Uploading an existing file**
3. 将 `ContractScanner` 文件夹内的所有文件（包括 `.github` 文件夹）拖拽上传
4. 填写提交信息：`Initial commit`
5. 点击 **Commit changes**

**方法二：通过 Git 命令上传**

```bash
# 进入项目目录
cd ContractScanner

# 初始化 Git 仓库
git init

# 添加所有文件
git add .

# 提交
git commit -m "Initial commit"

# 关联远程仓库（将 YOUR_USERNAME 替换为你的 GitHub 用户名）
git remote add origin https://github.com/YOUR_USERNAME/ContractScanner.git

# 推送代码
git branch -M main
git push -u origin main
```

### 第三步：触发自动构建

代码推送后，GitHub Actions 会自动开始构建。你也可以手动触发：

1. 进入仓库页面
2. 点击顶部 **Actions** 标签
3. 左侧选择 **Build APK**
4. 点击右侧 **Run workflow** → **Run workflow**

### 第四步：下载 APK

1. 等待构建完成（约 5-10 分钟，首次构建需要下载依赖）
2. 进入 **Actions** 页面，点击最新的运行记录
3. 页面底部 **Artifacts** 区域，点击 **app-debug** 下载 ZIP 压缩包
4. 解压 ZIP，得到 `app-debug.apk`

### 第五步：安装到手机

1. 将 APK 文件传输到 Android 手机
2. 在手机上打开文件管理器，点击 APK
3. 允许安装未知来源应用
4. 完成安装

## 项目说明

- **最低系统要求**：Android 7.0（API 24）及以上
- **所需权限**：相机、震动、存储（Android 10 以下）
- **功能**：实时扫描合同文字，提取卖方公司名、订单号、签订日期，支持导出 Excel 和 PDF

## 构建状态

可以在仓库 **Actions** 页面查看每次构建的详细日志。如果构建失败，请检查日志中的错误信息。

## 常见问题

**Q: 构建失败怎么办？**
A: 进入 Actions 页面，点击失败的构建记录，查看日志。常见原因包括依赖下载失败（重试即可）或代码语法错误。

**Q: 可以修改代码后重新构建吗？**
A: 可以。修改代码后推送到仓库，Actions 会自动重新构建。或者进入 Actions 页面手动点击 **Run workflow**。

**Q: 构建的 APK 有效期多久？**
A: Artifacts 默认保留 30 天，建议及时下载保存。
