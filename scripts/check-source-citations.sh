#!/usr/bin/env bash
# check-source-citations.sh —— 对外源码内部引用门禁（CLAUDE.md §5.3）
#
# 模式集单一事实源 = 本脚本的 PATTERNS 数组（"名称 ERE" 一条元素）。
# 命中即非零退出并定位 file:line。
# 扫描范围：各模块 src/main 全树（*.java/*.xml）+ 各 pom.xml 的 <description> 块；
# 排除 .git/target/openspec/docs/.idea（过程文档本身允许引用）。
# 用法：bash scripts/check-source-citations.sh [--selftest]
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

PATTERNS=(
  'P_INTERNAL_DOC 设计说明书|概要设计|详设|设计文档|实施计划|路线图'
  'P_SECTION_REF §[0-9]'
  'P_PHASE \bPhase[ _-]?[0-9]'
  'P_TASK_NO \bP[0-9]+-[0-9]+\b'
  'P_TASK_FAMILY \b[TS][0-9]+-[0-9]+\b'
  'P_MILESTONE \bM[0-9]+\b'
  'P_DECISION \bD[0-9]+\b'
  'P_OPENSPEC openspec|spec "[^"]+"'
)

src_main_dirs() {
  find "$1" \( -name .git -o -name target -o -name openspec -o -name docs -o -name .idea \) -prune -o \
    -type d -path '*/src/main' -print
}
pom_files() {
  find "$1" \( -name .git -o -name target -o -name openspec \) -prune -o -type f -name pom.xml -print
}
# 提取各 pom 的 <description> 块内容，输出 file:line: text
pom_descriptions() {
  local f
  for f in "$@"; do
    [ -f "$f" ] || continue
    awk -v FN="$f" '/<description>/{d=1} d{print FN":"FNR": "$0} /<\/description>/{d=0}' "$f"
  done
}

# scan_tree <root>：任一模式命中则打印 "模式名<TAB>file:line: 内容"
scan_tree() {
  local r="$1" e name ere d hit
  while read -r name ere; do
    for d in $(src_main_dirs "$r"); do
      hit=$(grep -R -n -E "$ere" --include='*.java' --include='*.xml' "$d" 2>/dev/null || true)
      [ -n "$hit" ] && printf '%s\n' "$hit" | sed "s|^|$name\t|"
    done
    hit=$(pom_descriptions $(pom_files "$r") | grep -E "$ere" || true)
    [ -n "$hit" ] && printf '%s\n' "$hit" | sed "s|^|$name\t|"
  done < <(for e in "${PATTERNS[@]}"; do printf '%s %s\n' "${e%% *}" "${e#* }"; done)
}

selftest() {
  local tmp ok=1 e name ere
  tmp=$(mktemp -d); trap 'rm -rf "$tmp"' RETURN
  for e in "${PATTERNS[@]}"; do
    name="${e%% *}"; ere="${e#* }"
    local pkg="$tmp/$name/src/main/java/x"
    mkdir -p "$pkg"
    case "$name" in
      P_INTERNAL_DOC) echo '/** 见概要设计。 */ class A{}' ;;
      P_SECTION_REF)  echo '/** 见 §3.4 节。 */ class A{}' ;;
      P_PHASE)        echo '/** Phase 1 引入。 */ class A{}' ;;
      P_TASK_NO)      echo '/** 任务 P1-03 要求。 */ class A{}' ;;
      P_TASK_FAMILY)  echo '/** 归属 T4-02 家族。 */ class A{}' ;;
      P_MILESTONE)    echo '/** M2 交付。 */ class A{}' ;;
      P_DECISION)     echo '/** 决策 D12 落地。 */ class A{}' ;;
      P_OPENSPEC)     echo '/** 见 spec "mapping-layer"。 */ class A{}' ;;
    esac > "$pkg/A.java"
    echo '<project><description>里程碑专用说明</description></project>' > "$tmp/$name/pom.xml"
    hit=$(scan_tree "$tmp/$name")
    if ! grep -q "^$name"$'\t' <<<"$hit"; then echo "SELFTEST FAIL[$name]: 夹具未命中（$ere）"; ok=0; fi
    # 干净夹具：同目录族内不得有任何模式命中
    local clean="$tmp/${name}_clean"
    mkdir -p "$clean/src/main/java/x"
    echo '/** 普通契约说明：线程安全，无共享可变状态。 */ class B{}' > "$clean/src/main/java/x/B.java"
    echo '<project><description>普通的模块描述文本</description></project>' > "$clean/pom.xml"
    hit=$(scan_tree "$clean")
    if [ -n "$hit" ]; then echo "SELFTEST FAIL[$name]: 干净夹具误报"$'\n'"$hit"; ok=0; fi
  done
  [ $ok -eq 1 ] && echo "SELFTEST OK（${#PATTERNS[@]} 模式：夹具全命中、干净夹具零误报）"
  return $((1 - ok))
}

if [ "${1:-}" = "--selftest" ]; then selftest; exit $?; fi

HITS=$(scan_tree "$ROOT")
if [ -n "$HITS" ]; then
  echo "发现内部过程引用（对外源码/发布元数据禁用，见 scripts/check-source-citations.sh 头注释）：" >&2
  printf '%s\n' "$HITS" | sort -u | head -50 >&2
  exit 1
fi
echo "check-source-citations: OK（src/main 与 pom description 无内部引用）"
