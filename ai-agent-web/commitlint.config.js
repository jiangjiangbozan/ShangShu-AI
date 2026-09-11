/**
 * commitlint 配置
 * 依据《企业Java后端开发规范.md》§2.4：提交规范使用 Conventional Commits
 * 详见 https://commitlint.js.org/reference/rules.html
 */
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // type 枚举
    'type-enum': [
      2,
      'always',
      [
        'feat', // 新功能
        'fix', // Bug 修复
        'docs', // 文档变更
        'style', // 代码格式（不影响功能）
        'refactor', // 重构（既不是新功能也不是修 Bug）
        'perf', // 性能优化
        'test', // 测试相关
        'build', // 构建系统或外部依赖变更
        'ci', // CI 配置
        'chore', // 杂项（不修改 src 或测试）
        'revert', // 回滚提交
      ],
    ],
    // type 不能为空
    'type-empty': [2, 'never'],
    // subject 不能为空
    'subject-empty': [2, 'never'],
    // subject 不能以句号结尾
    'subject-full-stop': [0],
    // subject 大小写
    'subject-case': [0],
    // header 最大长度
    'header-max-length': [2, 'always', 100],
  },
}
