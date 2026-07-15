#!/bin/bash
# Push A/B test fixes and open PR
# Run: bash push_fixes.sh

cd /d/IVD
echo "Pushing branch..."
git push -u origin fix/ab-test-critical-high

echo ""
echo "Done! To create a PR, visit:"
echo "https://github.com/wenqiangshao0214-lab/ZLTH-ZSPD/pull/new/fix/ab-test-critical-high"
