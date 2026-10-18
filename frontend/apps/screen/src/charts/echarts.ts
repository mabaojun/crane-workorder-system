// ECharts 按需引入（echarts/core + 必要 charts/components/renderer，减小包体）
import * as echarts from 'echarts/core'
import { BarChart, EffectScatterChart, PieChart, ScatterChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  MarkPointComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  EffectScatterChart,
  PieChart,
  ScatterChart,
  GridComponent,
  LegendComponent,
  MarkPointComponent,
  TooltipComponent,
  CanvasRenderer
])

export default echarts
