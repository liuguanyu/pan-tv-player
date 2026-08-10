## ADDED Requirements

### Requirement: Release 开启 R8 完整模式
系统 SHALL 在 Release 构建类型中开启 `minifyEnabled = true`，并使用 R8 完整模式（`isR8FullMode = true`）进行代码缩减、优化与混淆。

#### Scenario: Release APK 已混淆
- **WHEN** 构建 Release APK
- **THEN** 类名、方法名被混淆为短名称，且 `build/outputs/mapping/release/mapping.txt` 包含混淆映射

#### Scenario: Release APK 资源压缩
- **WHEN** 构建 Release APK 且 `shrinkResources = true`
- **THEN** 未使用的 drawable、layout、string 资源被移除

### Requirement: ProGuard 规则覆盖所有反射使用
系统 SHALL 提供完整的 `proguard-rules.pro`，为反射调用的类（Gson 序列化模型、Room Entity、Retrofit 接口）添加 `-keep` 规则。

#### Scenario: Gson 序列化模型不被混淆
- **WHEN** R8 处理 `@SerializedName` 注解的 model 类
- **THEN** 类成员名保持原样（`-keepclassmembers` 规则生效），JSON 反序列化正常

#### Scenario: Room Entity 不被混淆
- **WHEN** R8 处理 `@Entity` 注解的数据类
- **THEN** 类名和字段名不被混淆

#### Scenario: Retrofit 接口不被混淆
- **WHEN** R8 处理 `@GET`/`@POST` 注解的接口方法
- **THEN** 方法签名保持原样，运行时动态代理正常工作

### Requirement: APK 体积不超 Java 版
系统 SHALL 确保 Release APK 的体积 ≤ 原 Java 版 APK 体积 − 35MB（移除 VLC 后的预期缩减）。

#### Scenario: Release APK 体积检查
- **WHEN** 构建 Release APK
- **THEN** APK 大小 ≤ 15MB（含 FFmpeg `arm64-v8a` 单一 ABI ~5MB）

### Requirement: Kotlin 元数据保留规则
系统 SHALL 在 ProGuard 规则中保留 `kotlin.Metadata` 注解，确保 KSP 生成的代码正常反射。

#### Scenario: kotlin.Metadata 不被剥离
- **WHEN** R8 全模式处理
- **THEN** `-keepattributes *Annotation*, InnerClasses, EnclosingMethod` 和 `-keep class kotlin.Metadata { *; }` 规则生效
